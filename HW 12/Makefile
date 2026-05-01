CC := gcc
CFLAGS := -std=c17 -Wall -Wextra -Wpedantic -g
TARGET := clox
SRCS := chunk.c compiler.c debug.c main.c memory.c memory_hardcore.c object.c scanner.c table.c value.c vm.c
OBJS := $(SRCS:.c=.o)

.PHONY: all run clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $(OBJS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

run: $(TARGET)
	./$(TARGET)

clean:
	rm -f $(OBJS) $(TARGET)
