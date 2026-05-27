@ECHO OFF
REM Maven Wrapper para Windows
REM Baixa e usa a versão exata do Maven definida em .mvn/wrapper/maven-wrapper.properties

SET MAVEN_WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
SET MAVEN_WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST %MAVEN_WRAPPER_JAR% (
    FOR /F "tokens=2 delims==" %%i IN ('findstr "wrapperUrl" %MAVEN_WRAPPER_PROPERTIES%') DO SET WRAPPER_URL=%%i
    ECHO Baixando Maven Wrapper de: %WRAPPER_URL%
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%MAVEN_WRAPPER_JAR%'"
)

java -jar %MAVEN_WRAPPER_JAR% %*
