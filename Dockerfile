FROM gcc:latest

COPY . /opt/cpp_test

WORKDIR /opt/cpp_test

RUN g++ -o HelloWorld src/HelloWorld.cpp

CMD ["./HelloWorld"]
