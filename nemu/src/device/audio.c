/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 * PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 * KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <SDL2/SDL_audio.h>
#include <SDL2/SDL_stdinc.h>
#include <common.h>
#include <device/map.h>
#include <SDL2/SDL.h>
#include <memory/host.h>

enum {
        reg_freq,
        reg_channels,
        reg_samples,
        reg_sbuf_size,
        reg_init,
        reg_count,
        nr_reg
};

static uint8_t *sbuf = NULL;
static uint32_t *audio_base = NULL;

static uint32_t head;
static uint32_t count;
static bool audio_opened;

static SDL_AudioSpec spec;

static void audio_lock(void)
{
        if (audio_opened)
                SDL_LockAudio();
}

static void audio_unlock(void)
{
        if (audio_opened)
                SDL_UnlockAudio();
}

static void audio_callback(void *userdata, Uint8 *stream, int len)
{
        uint32_t nread = (uint32_t)len > count ? count : (uint32_t)len;

        if (head + nread <= CONFIG_SB_SIZE)
                SDL_memcpy(stream, sbuf + head, nread);
        else {
                uint32_t first_part = CONFIG_SB_SIZE - head;
                SDL_memcpy(stream, sbuf + head, first_part);
                SDL_memcpy(stream + first_part, sbuf, nread - first_part);
        }

        head = (head + nread) % CONFIG_SB_SIZE;
        count -= nread;

        if (nread < (uint32_t)len)
                SDL_memset(stream + nread, 0, len - nread);
}

static void audio_init_device(void)
{
        Assert(audio_base[reg_freq] > 0, "Invalid audio frequency");
        Assert(audio_base[reg_channels] > 0, "Invalid audio channel count");
        Assert(audio_base[reg_samples] > 0, "Invalid audio sample count");

        if (audio_opened) {
                SDL_CloseAudio();
                audio_opened = false;
        }

        head = 0;
        count = 0;

        SDL_zero(spec);
        spec.freq = audio_base[reg_freq];
        spec.channels = audio_base[reg_channels];
        spec.format = AUDIO_S16SYS;
        spec.samples = audio_base[reg_samples];
        spec.callback = audio_callback;
        spec.userdata = NULL;

        if (SDL_OpenAudio(&spec, NULL) < 0)
                panic("Failed to open audio device: %s", SDL_GetError());

        audio_opened = true;
        SDL_PauseAudio(0);
}

static void audio_io_handler(uint32_t offset, int len, bool is_write)
{
        Assert(offset % sizeof(uint32_t) == 0,
               "Unaligned audio register access");
        Assert(len == sizeof(uint32_t), "Invalid audio register access size");

        uint32_t reg_index = offset / sizeof(uint32_t);
        Assert(reg_index < nr_reg, "Invalid audio register access");

        if (!is_write) {
                if (reg_index == reg_count) {
                        audio_lock();
                        audio_base[reg_count] = count;
                        audio_unlock();
                }
                return;
        }

        switch (reg_index) {
        case reg_freq:
        case reg_channels:
        case reg_samples:
                break;
        case reg_sbuf_size:
                panic("Cannot write audio buffer size register");
        case reg_init:
                audio_init_device();
                break;
        case reg_count: {
                uint32_t submitted = audio_base[reg_count];

                if (submitted == 0)
                        break;

                Assert(submitted <= CONFIG_SB_SIZE,
                       "Audio submission exceeds buffer size");

                audio_lock();
                Assert(submitted <= CONFIG_SB_SIZE - count,
                       "Audio submission exceeds free space");
                count += submitted;
                audio_unlock();
                break;
        }
        default:
                panic("Cannot arrive here");
        }
}

void init_audio()
{
        if (SDL_Init(SDL_INIT_AUDIO) != 0)
                Log("Faild to initialize SDL audio %s", SDL_GetError());

        head = 0;
        count = 0;
        audio_opened = false;

        uint32_t space_size = sizeof(uint32_t) * nr_reg;
        audio_base = (uint32_t *)new_space(space_size);
#ifdef CONFIG_HAS_PORT_IO
        add_pio_map("audio", CONFIG_AUDIO_CTL_PORT, audio_base, space_size,
                    audio_io_handler);
#else
        add_mmio_map("audio", CONFIG_AUDIO_CTL_MMIO, audio_base, space_size,
                     audio_io_handler);
#endif
        audio_base[reg_sbuf_size] = CONFIG_SB_SIZE;
        audio_base[reg_count] = 0;

        sbuf = (uint8_t *)new_space(CONFIG_SB_SIZE);
        add_mmio_map("audio-sbuf", CONFIG_SB_ADDR, sbuf, CONFIG_SB_SIZE, NULL);
}
