# Makefile for comp3310 assignment 2 based on lab 4

PROGS=Spider.class

default: $(PROGS)

%.class: %.java
	javac $*.java

run: $(PROGS)
	java Spider

clean:
	rm -f $(PROGS) *.class
