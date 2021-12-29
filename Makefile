.PHONY : all clean test latte install-java

all : latte

latte : runtime
	chmod u+x ./gradlew && ./gradlew nativeImage

test : runtime
	chmod u+x ./gradlew && ./gradlew test

clean:
	rm -f latc_x86 && rm -f ./lib/runtime.o && rm -rf ./build

runtime:
	gcc -m32 -c lib/runtime.c -o lib/runtime.o
