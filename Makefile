.PHONY : all clean test latte

all : latte

latte :
	chmod u+x ./gradlew && ./gradlew clean nativeImage

test :
	chmod u+x ./gradlew && ./gradlew clean test

clean:
	chmod u+x ./gradlew && ./gradlew clean

