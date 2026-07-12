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

#include <isa.h>
#include <memory/paddr.h>

void init_rand();
void init_log(const char *log_file);
void init_mem();
void init_difftest(char *ref_so_file, long img_size, int port);
void init_device();
void init_sdb();
void init_disasm();

static void welcome()
{
        Log("Trace: %s", MUXDEF(CONFIG_TRACE, ANSI_FMT("ON", ANSI_FG_GREEN),
                                ANSI_FMT("OFF", ANSI_FG_RED)));
        IFDEF(CONFIG_TRACE,
              Log("If trace is enabled, a log file will be generated "
                  "to record the trace. This may lead to a large log file. "
                  "If it is not necessary, you can disable it in menuconfig"));
        Log("Build time: %s, %s", __TIME__, __DATE__);
        printf("Welcome to %s-NEMU!\n",
               ANSI_FMT(str(__GUEST_ISA__), ANSI_FG_YELLOW ANSI_BG_RED));
        printf("For help, type \"help\"\n");
}

#ifndef CONFIG_TARGET_AM
#include <getopt.h>

void sdb_set_batch_mode();

static char *log_file = NULL;
static char *diff_so_file = NULL;
static char *img_file = NULL;
static int difftest_port = 1234;

static long load_img()
{
        if (img_file == NULL) {
                Log("No image is given. Use the default build-in image.");
                return 4096; // built-in image size
        }

        FILE *fp = fopen(img_file, "rb");
        Assert(fp, "Can not open '%s'", img_file);

        fseek(fp, 0, SEEK_END);
        long size = ftell(fp);

        Log("The image is %s, size = %ld", img_file, size);

        fseek(fp, 0, SEEK_SET);
        int ret = fread(guest_to_host(RESET_VECTOR), size, 1, fp);
        assert(ret == 1);

        fclose(fp);
        return size;
}

#ifdef CONFIG_FTRACE
FtraceELF elf = {};

static void ftrace_elf_destroy(void)
{
        free(elf.strtab);
        free(elf.symtab);
        free(elf.shdr);
        free(elf.ehdr);

        elf.strtab = NULL;
        elf.symtab = NULL;
        elf.shdr = NULL;
        elf.ehdr = NULL;
        memset(&elf, 0, sizeof(elf));
}

static void ftrace_elf_init(void)
{
        FILE *fp = NULL;
        size_t ret;
        int found_symtab = 0;

        if (elf.path == NULL) {
                Log("No ftrace file is given.");
                return;
        }

        fp = fopen(elf.path, "rb");
        Assert(fp != NULL, "Cannot open '%s'", elf.path);

        ftrace_elf_destroy();

        elf.ehdr = malloc(sizeof(Elf32_Ehdr));
        if (elf.ehdr == NULL) {
                Log("No memory.");
                goto cleanup;
        }

        ret = fread(elf.ehdr, sizeof(Elf32_Ehdr), 1, fp);
        if (ret != 1) {
                Log("Failed to read ELF header.");
                goto cleanup;
        }

        if (elf.ehdr->e_ident[EI_MAG0] != ELFMAG0 ||
            elf.ehdr->e_ident[EI_MAG1] != ELFMAG1 ||
            elf.ehdr->e_ident[EI_MAG2] != ELFMAG2 ||
            elf.ehdr->e_ident[EI_MAG3] != ELFMAG3) {
                Log("Invalid ELF file.");
                goto cleanup;
        }

        if (elf.ehdr->e_shoff == 0 || elf.ehdr->e_shnum == 0) {
                Log("Invalid section header.");
                goto cleanup;
        }

        if (fseek(fp, elf.ehdr->e_shoff, SEEK_SET) != 0) {
                Log("fseek failed.");
                goto cleanup;
        }

        elf.shdr = malloc(sizeof(Elf32_Shdr) * elf.ehdr->e_shnum);

        if (elf.shdr == NULL) {
                Log("No memory.");
                goto cleanup;
        }

        ret = fread(elf.shdr, sizeof(Elf32_Shdr), elf.ehdr->e_shnum, fp);

        if (ret != elf.ehdr->e_shnum) {
                Log("Failed to read section headers.");
                goto cleanup;
        }

        for (int i = 0; i < elf.ehdr->e_shnum; i++) {
                if (elf.shdr[i].sh_type != SHT_SYMTAB)
                        continue;

                found_symtab = 1;

                elf.symtab_num = elf.shdr[i].sh_size / sizeof(Elf32_Sym);

                elf.symtab = malloc(elf.shdr[i].sh_size);

                if (elf.symtab == NULL) {
                        Log("No memory.");
                        goto cleanup;
                }

                if (fseek(fp, elf.shdr[i].sh_offset, SEEK_SET) != 0) {
                        Log("fseek failed.");
                        goto cleanup;
                }

                ret = fread(elf.symtab, sizeof(Elf32_Sym), elf.symtab_num, fp);

                if (ret != (size_t)elf.symtab_num) {
                        Log("Failed to read symbol table.");
                        goto cleanup;
                }

                int strtab_index = elf.shdr[i].sh_link;

                if (strtab_index >= elf.ehdr->e_shnum) {
                        Log("Invalid string table index.");
                        goto cleanup;
                }

                elf.strtab = malloc(elf.shdr[strtab_index].sh_size);

                if (elf.strtab == NULL) {
                        Log("No memory.");
                        goto cleanup;
                }

                if (fseek(fp, elf.shdr[strtab_index].sh_offset, SEEK_SET) !=
                    0) {
                        Log("fseek failed.");
                        goto cleanup;
                }

                ret = fread(elf.strtab, 1, elf.shdr[strtab_index].sh_size, fp);

                if (ret != elf.shdr[strtab_index].sh_size) {
                        Log("Failed to read string table.");
                        goto cleanup;
                }

                break;
        }

        if (!found_symtab) {
                Log("No symbol table found.");
                goto cleanup;
        }

        fclose(fp);
        return;

cleanup:

        if (fp != NULL)
                fclose(fp);

        ftrace_elf_destroy();
}

#endif

static int parse_args(int argc, char *argv[])
{
        const struct option table[] = {
                {"batch", no_argument, NULL, 'b'},
                {"log", required_argument, NULL, 'l'},
                {"diff", required_argument, NULL, 'd'},
                {"port", required_argument, NULL, 'p'},
                {"help", no_argument, NULL, 'h'},
                {"image", required_argument, NULL, 'i'},
                {0, 0, NULL, 0},
        };
        int o;
        while ((o = getopt_long(argc, argv, "-bhl:d:p:", table, NULL)) != -1) {
                switch (o) {
                case 'b':
                        sdb_set_batch_mode();
                        break;
                case 'p':
                        sscanf(optarg, "%d", &difftest_port);
                        break;
                case 'l':
                        log_file = optarg;
                        break;
                case 'd':
                        diff_so_file = optarg;
                        break;
                case 'i':
                        IFDEF(CONFIG_FTRACE, elf.path = optarg,
                              ftrace_elf_init());
                        break;
                case 1:
                        img_file = optarg;
                        return 0;
                default:
                        printf("Usage: %s [OPTION...] IMAGE [args]\n\n",
                               argv[0]);
                        printf("\t-b,--batch              run with batch "
                               "mode\n");
                        printf("\t-l,--log=FILE           output log to "
                               "FILE\n");
                        printf("\t-d,--diff=REF_SO        run DiffTest with "
                               "reference REF_SO\n");
                        printf("\t-i,--image=FILE         load image from "
                               "FILE\n"); //  从文件加载镜像
                        printf("\t-p,--port=PORT          run DiffTest with "
                               "port PORT\n");
                        printf("\n");
                        exit(0);
                }
        }
        return 0;
}

void init_monitor(int argc, char *argv[])
{
        /* Perform some global initialization. */

        /* Parse arguments. */
        parse_args(argc, argv);

        /* Set random seed. */
        init_rand();

        /* Open the log file. */
        init_log(log_file);

        /* Initialize memory. */
        init_mem();

        /* Initialize devices. */
        IFDEF(CONFIG_DEVICE, init_device());

        /* Perform ISA dependent initialization. */
        init_isa();

        /* Load the image to memory. This will overwrite the built-in image. */
        long img_size = load_img();

        /* Initialize differential testing. */
        init_difftest(diff_so_file, img_size, difftest_port);

        /* Initialize the simple debugger. */
        IFDEF(CONFIG_SDB, init_sdb());

        IFDEF(CONFIG_ITRACE, init_disasm());

        /* Display welcome message. */
        welcome();
}
#else // CONFIG_TARGET_AM
static long load_img()
{
        extern char bin_start, bin_end;
        size_t size = &bin_end - &bin_start;
        Log("img size = %ld", size);
        memcpy(guest_to_host(RESET_VECTOR), &bin_start, size);
        return size;
}

void am_init_monitor()
{
        init_rand();
        init_mem();
        init_isa();
        load_img();
        IFDEF(CONFIG_DEVICE, init_device());
        welcome();
}
#endif
