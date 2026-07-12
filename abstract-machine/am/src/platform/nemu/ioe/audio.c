#include <am.h>
#include <nemu.h>
#include <string.h>

#define AUDIO_FREQ_ADDR (AUDIO_ADDR + 0x00)
#define AUDIO_CHANNELS_ADDR (AUDIO_ADDR + 0x04)
#define AUDIO_SAMPLES_ADDR (AUDIO_ADDR + 0x08)
#define AUDIO_SBUF_SIZE_ADDR (AUDIO_ADDR + 0x0c)
#define AUDIO_INIT_ADDR (AUDIO_ADDR + 0x10)
#define AUDIO_COUNT_ADDR (AUDIO_ADDR + 0x14)

static uint32_t tail = 0;

static inline uint32_t audio_count(void)
{
        return inl(AUDIO_COUNT_ADDR);
}

static inline uint32_t audio_bufsize(void)
{
        return inl(AUDIO_SBUF_SIZE_ADDR);
}

/* Read returns pending bytes; write submits newly produced bytes. */
static inline void audio_submit(uint32_t len)
{
        outl(AUDIO_COUNT_ADDR, len);
}

static inline volatile uint8_t *audio_buffer(void)
{
        return (volatile uint8_t *)AUDIO_SBUF_ADDR;
}

static void audio_wait_space(uint32_t needed, uint32_t bufsize)
{
        while (needed > bufsize - audio_count()) {
        }
}

static void audio_write_ring(const uint8_t *src, uint32_t pos, uint32_t len,
                             uint32_t bufsize)
{
        volatile uint8_t *dst = audio_buffer();

        if (pos + len <= bufsize)
                memcpy((void *)(dst + pos), src, len);
        else {
                uint32_t first = bufsize - pos;
                memcpy((void *)(dst + pos), src, first);
                memcpy((void *)dst, src + first, len - first);
        }
}

void __am_audio_init()
{
        tail = 0;
}

void __am_audio_config(AM_AUDIO_CONFIG_T *cfg)
{
        cfg->present = true;
        cfg->bufsize = audio_bufsize();
}

void __am_audio_ctrl(AM_AUDIO_CTRL_T *ctrl)
{
        tail = 0;
        outl(AUDIO_FREQ_ADDR, ctrl->freq);
        outl(AUDIO_CHANNELS_ADDR, ctrl->channels);
        outl(AUDIO_SAMPLES_ADDR, ctrl->samples);
        outl(AUDIO_INIT_ADDR, 1);
}

void __am_audio_status(AM_AUDIO_STATUS_T *stat)
{
        stat->count = audio_count();
}

void __am_audio_play(AM_AUDIO_PLAY_T *ctl)
{
        uintptr_t start = (uintptr_t)ctl->buf.start;
        uintptr_t end = (uintptr_t)ctl->buf.end;

        if (end <= start)
                return;

        const uint8_t *src = (const uint8_t *)start;
        uintptr_t remaining = end - start;
        uint32_t bufsize = audio_bufsize();

        while (remaining > 0) {
                uint32_t chunk = remaining > (uintptr_t)bufsize
                                         ? bufsize
                                         : (uint32_t)remaining;

                audio_wait_space(chunk, bufsize);
                audio_write_ring(src, tail, chunk, bufsize);

                tail = (tail + chunk) % bufsize;
                audio_submit(chunk);

                src += chunk;
                remaining -= chunk;
        }
}
