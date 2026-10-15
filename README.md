# User Document Storage API

A clean, extensible REST API that lets you search and download user files stored in Amazon S3. Built with Spring Boot 4 and AWS SDK v2, ready to run locally in one command with Docker + LocalStack.

---

## Table of Contents

- [Architecture](#architecture)
- [Design Decisions](#design-decisions)
- [API Reference](#api-reference)
- [Running Locally](#running-locally)
- [Configuration](#configuration)
- [Tests](#tests)
- [Trade-offs & Future Improvements](#trade-offs--future-improvements)

---

## Architecture

### Package Layout

```
src/main/java/com/priyanshu/user_document_storage_api/
├── config/
│   ├── S3Config.java                          # S3Client + S3Presigner beans
│   ├── StorageProperties.java                 # @ConfigurationProperties (no hardcoding)
│   └── SwaggerConfig.java                     # OpenAPI / Swagger setup
├── controller/
│   └── FileController.java                    # REST endpoints
├── service/
│   ├── FileStorageService.java                # Storage interface
│   ├── impl/
│   │   └── S3FileStorageService.java          # S3 implementation
│   └── search/
│       ├── FileSearchStrategy.java            # Search interface
│       └── impl/
│           └── ContainsIgnoreCaseSearchStrategy.java
├── dto/
│   ├── FileMetadata.java                      # name, sizeBytes, lastModified
│   ├── PaginatedResponse.java                 # items + continuationToken
│   └── ApiError.java                          # consistent error envelope
└── exception/
    ├── FileNotFoundException.java
    ├── StorageException.java
    └── GlobalExceptionHandler.java            # @RestControllerAdvice
```

### S3 Key Layout

```
user-document-storage/          ← one bucket
└── users/
    ├── sandy/
    │   ├── logistics_report.pdf
    │   ├── Logistics_Q3.xlsx
    │   └── invoice_2024.txt
    └── john/
        ├── logistics_plan.docx
        └── resume.pdf
```

Each user's files live under the prefix `users/{userName}/`. Listing is always scoped to that prefix, so S3 never returns another user's keys.

### Component Diagram

```
Client
  │
  ▼
FileController                 ← thin HTTP layer, no business logic
  │
  ▼
FileStorageService             ← interface (swappable backend)
  │
  ├── S3FileStorageService     ← S3 implementation (default)
  │     │
  │     └── FileSearchStrategy ← interface (swappable algorithm)
  │           │
  │           └── ContainsIgnoreCaseSearchStrategy
```

---

## Design Decisions

### 1. Two Interfaces → Easy Extension

| Interface | Purpose | Swap cost |
|---|---|---|
| `FileStorageService` | list / download / upload | New class only |
| `FileSearchStrategy` | filename matching algorithm | New class only |

Adding Google Cloud Storage or prefix-only search means writing one class and swapping the Spring bean — zero controller changes.

### 2. Optimised S3 Listing (Prefix + Pagination)

S3's `ListObjectsV2` is called with `prefix = users/{userName}/`, so the response set is already scoped to one user. The service then:

1. Iterates the page, applying `FileSearchStrategy.matches()` to each filename.
2. Accumulates matches until `pageSize` is reached **or** the page is exhausted.
3. Loops (fetching the next S3 page via `continuationToken`) only when more matches are still needed.
4. Returns the S3 continuation token to the caller so the next request picks up exactly where it left off.

**No file content is ever downloaded during search** — only `S3Object` metadata (key, size, `lastModified`) is read.

### 3. Two Download Modes

| Mode | Endpoint | Best for |
|---|---|---|
| **Server-stream** | `GET /download` | Small files, staying behind the API |
| **Presigned URL** | `GET /download-link` | Large files, CDN, client downloads direct from S3 |

The presigned URL (15-minute TTL) offloads bandwidth from the application server entirely.

### 4. Two Upload Modes

| Mode | Endpoint | Best for |
|---|---|---|
| **Multipart POST** | `POST /files` | Simple uploads through the server (≤ 50 MB) |
| **Presigned URL** | `GET /upload-link` | Large files, direct client-to-S3 upload |

### 5. Security — Path Traversal Prevention

`userName` and `fileName` are validated in `S3FileStorageService.validatePath()` to block `../`, `/`, and `\` before any S3 key is constructed. A malicious input like `../admin` is rejected with a `400 Bad Request` before it ever reaches S3.

### 6. Zero Hardcoded Config

All S3 settings (`bucketName`, `region`, `endpoint`, `accessKey`, `secretKey`) are bound via `@ConfigurationProperties(prefix = "storage.s3")` and overridable with environment variables. The `local` profile points at LocalStack; production reads real AWS credentials through the default credential chain.

---

## API Reference

Base URL: `http://localhost:8080`  
Swagger UI: [`http://localhost:8080/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html)

### Search Files

```
GET /api/v1/users/{userName}/files
```

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `search` | string | No | `""` | Case-insensitive substring to match against filenames |
| `pageSize` | int | No | `50` | Max results to return |
| `continuationToken` | string | No | — | Token from previous response for next page |

**Response `200 OK`**
```json
{
  "items": [
    {
      "fileName": "logistics_report.pdf",
      "sizeBytes": 34,
      "lastModified": "2026-10-14T13:12:00Z"
    }
  ],
  "continuationToken": "abc123..."
}
```
> `continuationToken` is `null` when there are no more pages.

---

### Download File (Server Stream)

```
GET /api/v1/users/{userName}/files/download?fileName={fileName}
```

Returns the raw file bytes with `Content-Disposition: attachment`.

---

### Get Presigned Download URL

```
GET /api/v1/users/{userName}/files/download-link?fileName={fileName}
```

**Response `200 OK`**
```json
{
  "url": "https://user-document-storage.s3.amazonaws.com/users/sandy/logistics_report.pdf?X-Amz-Signature=..."
}
```

The presigned URL expires in **15 minutes**.

---

### Upload File (Multipart)

```
POST /api/v1/users/{userName}/files
Content-Type: multipart/form-data

file=@/path/to/file.pdf
```

Max file size: **50 MB** (configurable via `spring.servlet.multipart.max-file-size`).

---

### Get Presigned Upload URL

```
GET /api/v1/users/{userName}/files/upload-link?fileName={fileName}&contentType={contentType}
```

**Response `200 OK`**
```json
{
  "uploadUrl": "https://user-document-storage.s3.amazonaws.com/users/sandy/report.pdf?X-Amz-Signature=..."
}
```

Then `PUT` your file bytes directly to that URL within 15 minutes.

---

### Error Responses

All errors return a consistent JSON envelope:

```json
{
  "error": "Not Found",
  "message": "File not found: missing.pdf",
  "status": 404,
  "timestamp": "2026-10-15T06:00:00Z"
}
```

| HTTP Status | Trigger |
|---|---|
| `400 Bad Request` | Invalid input, path traversal attempt |
| `404 Not Found` | File or route does not exist |
| `413 Content Too Large` | File exceeds 50 MB limit |
| `502 Bad Gateway` | S3 / storage backend error |
| `500 Internal Server Error` | Unexpected server error |

---

## Running Locally

### Prerequisites

- Docker & Docker Compose
- Java 21
- A [LocalStack Auth Token](https://app.localstack.cloud/sign-in) (free tier works)

### One Command

```bash
export LOCALSTACK_AUTH_TOKEN=<your-token>
make start
```

This will:
1. Start LocalStack, create the `user-document-storage` bucket, and seed sample files.
2. Launch the Spring Boot app on `http://localhost:8080` with the `local` profile.

### Make Targets

```
make help          # Show all targets and variables
make up            # Start LocalStack + seed data only
make run           # Run the app (LocalStack must be up)
make start         # up + run (the one command)
make down          # Stop LocalStack
make reset         # Stop + wipe all LocalStack data
make logs          # Tail LocalStack logs
make seed          # Re-run the seed script
make s3-ls         # List all objects in the bucket
make test          # Run all JUnit tests
make build         # Build the jar (skips tests)
make search        # curl the search endpoint (see below)
make download      # curl the download endpoint
make upload        # curl the upload endpoint
make swagger       # Print the Swagger UI URL
```

### Quick Smoke Tests via `make`

```bash
# Search for "logistics" files belonging to sandy
make search USER_NAME=sandy SEARCH_TERM=logistics

# Download a specific file
make download USER_NAME=sandy FILE=logistics_report.pdf

# Upload a file
make upload USER_NAME=sandy FILE=./my-document.pdf
```

### Seeded Data

The `localstack-init/init-s3.sh` script seeds the following files on startup:

| User | File |
|---|---|
| `sandy` | `logistics_report.pdf`, `Logistics_Q3.xlsx`, `invoice_2024.txt`, `notes.txt` |
| `john` | `logistics_plan.docx`, `resume.pdf` |

---

## Configuration

### Profiles

| Profile | Config file | Target |
|---|---|---|
| `local` | `application-local.properties` | LocalStack on `localhost:4566` |
| `prod` | `application-prod.properties` | Real AWS (uses default credential chain) |
| `test` | `application-test.properties` | Unit / integration tests |

### Key Properties (`application-local.properties`)

```properties
storage.s3.bucket-name=user-document-storage
storage.s3.key-prefix=users/
storage.s3.region=${AWS_REGION:us-east-1}
storage.s3.endpoint=${AWS_ENDPOINT:http://localhost:4566}
storage.s3.access-key=${AWS_ACCESS_KEY_ID:test}
storage.s3.secret-key=${AWS_SECRET_ACCESS_KEY:test}
storage.s3.path-style-access-enabled=true
```

For production, set real `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` environment variables (or use an IAM instance profile) and remove `storage.s3.endpoint`.

---

## Tests

```bash
make test
# or
./gradlew test
```

---

[MIT LICENSE](./LICENSE)
