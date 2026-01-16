# 📄 Topic 13: Media Handling & Optimization

## 1. Problem Statement
Images and Files consume massive bandwidth and storage.
*   **Bandwidth Cost**: Serving 5MB images to 10k users = 50GB traffic ($$$).
*   **Upload Reliability**: Uploading a 1GB video on unstable mobile network often fails.

## 2. Key Questions to Solve
*   **Resumable Uploads**: How to implement chunked uploads (Tus protocol)?
*   **On-the-fly Processing**: Generating thumbnails (Lambda? Serverless?) without blocking the API.
*   **CDN Strategy**: Signed URLs for private content caching.

## 3. Direction
*   **Presigned URLs (S3)** for direct upload/download.
*   **Async Processing** for thumbnails.
