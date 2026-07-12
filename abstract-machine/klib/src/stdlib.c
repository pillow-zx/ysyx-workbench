#include <am.h>
#include <klib.h>
#include <klib-macros.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)
static unsigned long int next = 1;

int rand(void)
{
        // RAND_MAX assumed to be 32767
        next = next * 1103515245 + 12345;
        return (unsigned int)(next / 65536) % 32768;
}

void srand(unsigned int seed)
{
        next = seed;
}

int abs(int x)
{
        return (x < 0 ? -x : x);
}

int atoi(const char *nptr)
{
        int x = 0;
        while (*nptr == ' ') {
                nptr++;
        }
        while (*nptr >= '0' && *nptr <= '9') {
                x = x * 10 + *nptr - '0';
                nptr++;
        }
        return x;
}

/*
 * Boundary-tag allocator with explicit free list.
 *
 * Block layout (8-byte aligned):
 *   [ next_free *  | size+flag  |  user data ...  | size_copy ]
 *    ^-- header (8B)                                ^-- footer
 * User data returned at offset 8 (8-byte aligned).
 * Low bit of ->size: 1 = free, 0 = used.
 *
 * Free blocks maintain a sorted doubly-linked free list:
 *   ->next  at header offset 0 (block_t *next)
 *   ->prev  at data  offset 0 (first 4 bytes of user data area)
 * Both pointers are only valid when the block is in the free list.
 *
 * malloc: first-fit, split if remainder >= MIN_BLOCK, else bump allocate.
 * free:   mark free, coalesce with adjacent free blocks via boundary tags.
 */

typedef struct block {
        struct block *next;       /* free-list next; unused when allocated */
        size_t        size;       /* total size incl. hdr+ftr; LSB = free flag */
} block_t;

/* Offsets into user-data area when block is free */
#define BLK_PREV(b)     (*(struct block **)((char *)(b) + sizeof(block_t)))
#define SET_PREV(b, p)  (BLK_PREV(b) = (p))

/* Size (without free flag) */
#define BLK_SZ(b)       ((b)->size & ~(size_t)1)

/* Free? */
#define BLK_FREE(b)     ((b)->size & 1)

/* Footer address */
#define BLK_FTR(b)      ((size_t *)((char *)(b) + BLK_SZ(b) - sizeof(size_t)))

/* Next block in linear address order */
#define BLK_NEXT(b)     ((block_t *)((char *)(b) + BLK_SZ(b)))

/* User data pointer */
#define BLK_DATA(b)     ((char *)(b) + sizeof(block_t))

/* Minimum block that can usefully hold a free block's metadata */
#define MIN_BLOCK       ((size_t)ROUNDUP((uintptr_t)(sizeof(block_t) + sizeof(block_t *) + sizeof(size_t)), 8))

/* ---- free-list helpers (doubly-linked, address-sorted) ---- */

static block_t *free_list;            /* head of free list */
static void    *heap_base;            /* original heap.start, saved once */

static void block_init(block_t *b, size_t sz, int is_free)
{
        b->size = sz | (is_free ? 1 : 0);
        b->next = NULL;
        *BLK_FTR(b) = b->size;
}

static void fl_remove(block_t *b)
{
        if (BLK_PREV(b))
                BLK_PREV(b)->next = b->next;
        else
                free_list = b->next;

        if (b->next)
                SET_PREV(b->next, BLK_PREV(b));
}

static void fl_insert(block_t *b)
{
        block_t *prev = NULL, *cur = free_list;
        while (cur && cur < b) {
                prev = cur;
                cur  = cur->next;
        }
        b->next = cur;
        SET_PREV(b, prev);
        if (cur)  SET_PREV(cur, b);
        if (prev) prev->next = b;
        else      free_list = b;
}

/* ---- public API ---- */

void *malloc(size_t size)
{
        // On native, malloc() will be called during initializaion of C runtime.
        // Therefore do not call panic() here, else it will yield a dead
        // recursion:
        //   panic() -> putchar() -> (glibc) -> malloc() -> panic()
#if !(defined(__ISA_NATIVE__) && defined(__NATIVE_USE_KLIB__))
        /* Save original heap base for backward-coalesce boundary checks */
        if (!heap_base) heap_base = heap.start;

        size_t asize = (size_t)ROUNDUP(size, 8);
        size_t need  = (size_t)ROUNDUP(sizeof(block_t) + asize + sizeof(size_t), 8);

        /* 1) First-fit search of free list */
        for (block_t *b = free_list; b; b = b->next) {
                size_t bsz = BLK_SZ(b);
                if (bsz < need) continue;

                size_t remain = bsz - need;
                if (remain >= MIN_BLOCK) {
                        /* Split */
                        fl_remove(b);
                        block_t *nb = (block_t *)((char *)b + need);
                        block_init(nb, remain, 1);
                        fl_insert(nb);
                        block_init(b, need, 0);
                } else {
                        /* Use whole block */
                        fl_remove(b);
                        block_init(b, bsz, 0);
                }

                void *ptr = BLK_DATA(b);
                memset(ptr, 0, asize);
                return ptr;
        }

        /* 2) Bump allocate from heap */
        block_t *b = (block_t *)heap.start;
        if (!IN_RANGE((void *)b, heap) || (char *)b + need > (char *)heap.end) {
                panic("malloc: out of memory");
        }
        block_init(b, need, 0);
        heap.start = (char *)heap.start + need;

        void *ptr = BLK_DATA(b);
        memset(ptr, 0, asize);
        return ptr;
#endif
        return NULL;
}

void free(void *ptr)
{
        if (!ptr) return;

#if !(defined(__ISA_NATIVE__) && defined(__NATIVE_USE_KLIB__))
        block_t *b = (block_t *)((char *)ptr - sizeof(block_t));

        if (BLK_FREE(b)) return;                /* double-free guard */

        /* 1) Mark free */
        size_t bsz = BLK_SZ(b);
        block_init(b, bsz, 1);

        /* 2) Coalesce with next block in memory if it is free */
        block_t *nb = BLK_NEXT(b);
        /* nb is valid if it lies within the block-organised region (< bump ptr) */
        if ((char *)nb < (char *)heap.start && BLK_FREE(nb)) {
                fl_remove(nb);
                bsz += BLK_SZ(nb);
                block_init(b, bsz, 1);
        }

        /* 3) Coalesce with previous block in memory via boundary tag */
        if ((char *)b > (char *)heap_base) {
                size_t prev_sz = *(size_t *)((char *)b - sizeof(size_t));
                block_t *pb = (block_t *)((char *)b - (prev_sz & ~(size_t)1));
                if (BLK_FREE(pb)) {
                        fl_remove(pb);
                        bsz += BLK_SZ(pb);
                        block_init(pb, bsz, 1);
                        b = pb;
                }
        }

        /* 4) Insert merged block into free list */
        fl_insert(b);
#endif
}

#endif
