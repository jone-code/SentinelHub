CLANG ?= clang
LLC   ?= llc
ARCH  := $(shell uname -m | sed 's/x86_64/x86/' | sed 's/aarch64/arm64/')

BPF_CFLAGS := -O2 -g -target bpf -D__TARGET_ARCH_$(ARCH) \
	-I/usr/include/$(shell uname -m)-linux-gnu \
	-Wno-compare-distinct-pointer-types

.PHONY: all clean

all: process_block.bpf.o

process_block.bpf.o: process_block.bpf.c
	$(CLANG) $(BPF_CFLAGS) -c $< -o $@

clean:
	rm -f *.bpf.o
