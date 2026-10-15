#!/bin/bash
set -e

BUCKET="user-document-storage"

echo "Creating bucket: $BUCKET"
awslocal s3 mb "s3://$BUCKET" || true

# Seed sample files: one folder (prefix) per user
seed() {
  local user="$1" name="$2" content="$3"
  echo "$content" | awslocal s3 cp - "s3://$BUCKET/users/$user/$name"
}

seed sandy logistics_report.pdf   "Sample logistics report for sandy"
seed sandy Logistics_Q3.xlsx      "Sample Q3 logistics sheet for sandy"
seed sandy invoice_2024.txt       "Sample invoice for sandy"
seed sandy notes.txt              "Sample notes for sandy"
seed john  logistics_plan.docx    "Sample logistics plan for john"
seed john  resume.pdf             "Sample resume for john"

echo "Seed complete. Objects:"
awslocal s3 ls "s3://$BUCKET/users/" --recursive
