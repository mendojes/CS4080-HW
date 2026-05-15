#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "memory_hardcore.h"

#define HARDCORE_ALIGNMENT 8
#define HARDCORE_MIN_SPLIT_PAYLOAD 16

typedef struct Block {
  size_t size;              // payload size in bytes
  bool free;                // true if this block is free
  struct Block* next;       // next block in heap order
  struct Block* prev;       // previous block in heap order
  struct Block* nextFree;   // next block in free list
  struct Block* prevFree;   // previous block in free list
} Block;

static uint8_t* gHeap = NULL;
static size_t gHeapSize = 0;

static Block* gFirstBlock = NULL;
static Block* gFreeList = NULL;

static size_t align8(size_t n) {
  return (n + (HARDCORE_ALIGNMENT - 1)) & ~(size_t)(HARDCORE_ALIGNMENT - 1);
}

static size_t headerSize(void) {
  return align8(sizeof(Block));
}

static void freeListRemove(Block* block) {
  if (block == NULL) return;

  if (block->prevFree != NULL) {
    block->prevFree->nextFree = block->nextFree;
  } else {
    gFreeList = block->nextFree;
  }

  if (block->nextFree != NULL) {
    block->nextFree->prevFree = block->prevFree;
  }

  block->prevFree = NULL;
  block->nextFree = NULL;
}

static void freeListInsert(Block* block) {
  if (block == NULL) return;

  block->free = true;
  block->prevFree = NULL;
  block->nextFree = gFreeList;

  if (gFreeList != NULL) {
    gFreeList->prevFree = block;
  }

  gFreeList = block;
}

static Block* payloadToBlock(void* payload) {
  return (Block*)((uint8_t*)payload - headerSize());
}

static void* blockToPayload(Block* block) {
  return (void*)((uint8_t*)block + headerSize());
}

static Block* findFit(size_t wanted) {
  for (Block* block = gFreeList; block != NULL; block = block->nextFree) {
    if (block->size >= wanted) return block;
  }
  return NULL;
}

static void splitBlock(Block* block, size_t wanted) {
  const size_t hs = headerSize();
  wanted = align8(wanted);

  if (block->size < wanted + hs + HARDCORE_MIN_SPLIT_PAYLOAD) {
    return;
  }

  uint8_t* raw = (uint8_t*)block;
  Block* tail = (Block*)(raw + hs + wanted);

  tail->size = block->size - wanted - hs;
  tail->free = true;
  tail->prev = block;
  tail->next = block->next;
  tail->prevFree = NULL;
  tail->nextFree = NULL;

  if (tail->next != NULL) {
    tail->next->prev = tail;
  }

  block->size = wanted;
  block->next = tail;

  freeListInsert(tail);
}

static Block* coalesce(Block* block) {
  const size_t hs = headerSize();

  if (block->next != NULL && block->next->free) {
    Block* next = block->next;
    freeListRemove(next);

    block->size += hs + next->size;
    block->next = next->next;

    if (block->next != NULL) {
      block->next->prev = block;
    }
  }

  if (block->prev != NULL && block->prev->free) {
    Block* prev = block->prev;
    freeListRemove(prev);

    prev->size += hs + block->size;
    prev->next = block->next;

    if (prev->next != NULL) {
      prev->next->prev = prev;
    }

    block = prev;
  }

  return block;
}

static void* allocBlock(size_t size) {
  if (size == 0) return NULL;

  size = align8(size);
  Block* block = findFit(size);
  if (block == NULL) return NULL;

  freeListRemove(block);
  block->free = false;

  splitBlock(block, size);

  return blockToPayload(block);
}

static void freeBlock(void* pointer) {
  if (pointer == NULL) return;

  Block* block = payloadToBlock(pointer);
  assert(!block->free && "double free detected");

  block->free = true;
  block = coalesce(block);
  freeListInsert(block);
}

bool hardcore_allocator_init(size_t heapBytes) {
  if (gHeap != NULL || heapBytes == 0) return false;

  heapBytes = align8(heapBytes);
  if (heapBytes <= headerSize() + HARDCORE_MIN_SPLIT_PAYLOAD) return false;

  gHeap = (uint8_t*)malloc(heapBytes);
  if (gHeap == NULL) return false;

  gHeapSize = heapBytes;

  gFirstBlock = (Block*)gHeap;
  gFirstBlock->size = heapBytes - headerSize();
  gFirstBlock->free = true;
  gFirstBlock->next = NULL;
  gFirstBlock->prev = NULL;
  gFirstBlock->nextFree = NULL;
  gFirstBlock->prevFree = NULL;

  gFreeList = NULL;
  freeListInsert(gFirstBlock);

  return true;
}

void hardcore_allocator_shutdown(void) {
  if (gHeap != NULL) {
    free(gHeap);
  }

  gHeap = NULL;
  gHeapSize = 0;
  gFirstBlock = NULL;
  gFreeList = NULL;
}

void* hardcore_reallocate(void* pointer, size_t oldSize, size_t newSize) {
  (void)oldSize; // We recover the real block size from the header.

  if (pointer == NULL) {
    return allocBlock(newSize);
  }

  if (newSize == 0) {
    freeBlock(pointer);
    return NULL;
  }

  newSize = align8(newSize);
  Block* block = payloadToBlock(pointer);

  if (block->size >= newSize) {
    splitBlock(block, newSize);
    return pointer;
  }

  if (block->next != NULL && block->next->free) {
    size_t combined = block->size + headerSize() + block->next->size;
    if (combined >= newSize) {
      Block* next = block->next;
      freeListRemove(next);

      block->size = combined;
      block->next = next->next;
      if (block->next != NULL) {
        block->next->prev = block;
      }

      splitBlock(block, newSize);
      return pointer;
    }
  }

  void* newPointer = allocBlock(newSize);
  if (newPointer == NULL) return NULL;

  memcpy(newPointer, pointer, block->size < newSize ? block->size : newSize);
  freeBlock(pointer);
  return newPointer;
}