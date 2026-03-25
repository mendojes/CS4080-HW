#ifndef clox_memory_hardcore_h
#define clox_memory_hardcore_h

#include "common.h"
#include <stddef.h>

#define HARDCORE_GROW_CAPACITY(capacity) \
((capacity) < 8 ? 8 : (capacity) * 2)

#define HARDCORE_GROW_ARRAY(type, pointer, oldCount, newCount) \
(type*)hardcore_reallocate((pointer), sizeof(type) * (oldCount), \
sizeof(type) * (newCount))

#define HARDCORE_FREE_ARRAY(type, pointer, oldCount) \
hardcore_reallocate((pointer), sizeof(type) * (oldCount), 0)

// Call once at interpreter startup.
// This is the single malloc() for the allocator.
bool hardcore_allocator_init(size_t heapBytes);

// Optional cleanup for tests/program shutdown.
void hardcore_allocator_shutdown(void);

// Drop-in reallocate() equivalent using only the custom heap.
void* hardcore_reallocate(void* pointer, size_t oldSize, size_t newSize);

#endif