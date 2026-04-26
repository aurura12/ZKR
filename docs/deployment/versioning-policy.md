# Container Versioning Policy

- Image tags must use `v1.N` format and increase by exactly `+1` for each new release.
- Do not skip numbers.
- Do not reuse an existing number for a different build.

## Required Process

1. Query the next version before every build:
   - `python3 scripts/next_image_version.py 192.168.66.223:5555 zhangqi_backend`
   - `python3 scripts/next_image_version.py 192.168.66.223:5555 zhangqi_frontend`
2. Build and tag images with the returned next version.
3. Update `docker-compose.yml` to the same version values.
4. Deploy with `docker compose up -d`.

## Rule

Any container iteration must keep continuous numbering.
