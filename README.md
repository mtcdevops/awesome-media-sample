---
topic: sample
languages:
  - java
products:
  - azure-media-services
---
# 미디어 인코더 표준의 사전 정의된 사전 설정을 사용하여 파일 인코딩

이 샘플은 기본 제공 프리셋과 HTTP URL 입력을 사용하여 작업을 제출하고, 스트리밍을 위해 출력 자산을 배포하고, 확인을 위해 결과를 다운로드하는 방법을 보여줍니다.

## 사전 요구 사항

* Java JDK 1.8 혹은 이상 버전
* Maven
* Azure Media Service 계정 관련 설정 해당 부분에 대한 메뉴얼은 다음 내용을 참고부탁드립니다. [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## 샘플 실행 방법

* Application에서 사용하는 변수값을 `appsettings.json` 에 설정 합니다.
* 미디어 서비스 API를 사용하기 위해 필요한 자격 증명은 [API 액세스](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to)를 참조하여 생성 합니다.
*  `src/main/resources/conf/appsettings.json` 설정 파일을 열고 파일에 값을 붙여넣습니다.
* 프로젝트 Clean 후 Build 합니다.

  터미널 창을 열고 이 프로젝트의 루트 폴더로 이동하여 `mvn clean compile`을 실행합니다.
* 프로젝트를 실행 합니다.

  `mvn exec:java`을 실행한 다음 출력 콘솔의 지침을 따릅니다.

## Key concepts

* [Encoding with Media Services](https://docs.microsoft.com/azure/media-services/latest/encoding-concept)
* [Transforms and Jobs](https://docs.microsoft.com/azure/media-services/latest/transforms-jobs-concept)
* [Standard Encoder formats and codecs](https://docs.microsoft.com/azure/media-services/latest/media-encoder-standard-formats)
* [Media Services job error codes](https://docs.microsoft.com/azure/media-services/latest/job-error-codes)

## Next steps

* [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
* [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)
