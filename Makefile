.PHONY : all clean test latte install-java

all : latte

latte :
	chmod u+x ./gradlew && ./gradlew clean nativeImage

test :
	chmod u+x ./gradlew && ./gradlew clean test

clean:
	rm -f latc_x86 && rm -f ./lib/runtime.o && rm -rf ./build

