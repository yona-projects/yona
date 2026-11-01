실제 `gpg` 2.4.8과 `git`이 만든 시험 자료이다(X-4). 일회용 키이며 비밀키는 보관하지 않는다.

- `ed-pub.asc`, `rsa-pub.asc`: 공개키(Ed25519, RSA 3072). 사용자 ID는 `Real Ed <real-ed@example.com>`, `Real Rsa <real-rsa@example.com>`
- `ed-commit.bin`, `rsa-commit.bin`: `git cat-file commit`으로 뽑은 서명 커밋 원문
- `ed-tag.bin`: `git cat-file tag`로 뽑은 서명 주석 태그 원문(Ed25519 키)
