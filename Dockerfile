FROM amazoncorretto:21-alpine-jdk

COPY core/target/evomaster.jar .

ENTRYPOINT [  \
    "java", \
    "-Xmx4G", \
    "-jar", "evomaster.jar", \
    "--runningInDocker", "true" \
]


###################
###### NOTES ######
###################
# Build
# docker build -t webfuzzing/evomaster  .
#
# Run
# docker run webfuzzing/evomaster  <options>
#
# Example remote BB
# docker run -v "/$(pwd)/generated_tests":/generated_tests webfuzzing/evomaster --blackBox true --bbSwaggerUrl https://api.apis.guru/v2/openapi.yaml  --outputFormat JAVA_JUNIT_4 --maxTime 10s --ratePerMinute 60
#
# Example local BB
# docker run -v "/$(pwd)/generated_tests":/generated_tests  webfuzzing/evomaster  --blackBox true --bbSwaggerUrl http://host.docker.internal:8080/v3/api-docs --maxTime 5s
#
# Example WB
# docker run -v "/$(pwd)/generated_tests":/generated_tests  webfuzzing/evomaster --dockerLocalhost true
#
# TODO and em.yaml
#
# Debugging
# docker run -it --entrypoint sh  webfuzzing/evomaster
#
#
#
#
#
#
#