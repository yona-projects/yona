Yona 실행 방법
===

압축이 풀린 디렉터리로 이동해서 yona를 실행합니다.

```
YONA_DATA=/yona-data;export YONA_DATA
bin/yona
```
**[주의!] 이때 bin 폴더 아래로 이동해서 직접 `yona`를 실행하지 않도록 유의해주세요**

최초 화면 확인
----
이제 웹 브라우저로 해당 서버 9000 포트(로컬환경이면 [http://127.0.0.1:9000](http://127.0.0.1:9000))에 접속하면 환영 페이지를 보실 수 있습니다. 

어드민 설정을 마치고 Yona를 다시 시작합니다.

**유의! Windows OS 사용자의 경우**
[#windows의-경우](yona-run-options.md#windows의-경우)를 꼭 참고해주세요


Tip - Windows에서 yona 백그라운드 처리
---
https://github.com/yona-projects/yona/issues/64


### 재시작 방법

- Linux/Uinx 계열에서는 kill pid 명령으로 서비스를 중단합니다.
- [간단한 재시작 쉘 예제](https://github.com/yona-projects/yona/blob/next/restart.sh)
- 윈도우즈 사용자는 ctrl-c 로 실행중인 배치파일을 종료합니다.

