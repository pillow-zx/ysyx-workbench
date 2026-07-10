/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <assert.h>
#include <string.h>

typedef uint32_t word_t;
#define BUF_SIZE 65536
#define MAX_DEPTH 4

// this should be enough
static char buf[BUF_SIZE] = {};
static char code_buf[BUF_SIZE + 128] = {}; // a little larger than `buf`
static char *code_format = "#include <stdio.h>\n"
                           "int main() { "
                           "  unsigned result = %s; "
                           "  printf(\"%%u\", result); "
                           "  return 0; "
                           "}";

static word_t pos = 0;
static word_t current_depth = 0;
static word_t choose(unsigned n)
{
        return rand() % n;
}

static void gen(char ch)
{
        assert(pos < BUF_SIZE - 1);
        buf[pos++] = ch;
        buf[pos] = '\0';
}

static void gen_num(void)
{
        int len = snprintf(buf + pos, BUF_SIZE - pos, "%u", choose(100));
        assert(len > 0);
        pos += len;
}

static char gen_rand_op(void)
{
        static const char ops[] = "+-*/";
        return ops[choose(sizeof(ops) - 1)];
}

static void gen_rand_expr(void);

static void gen_binary_expr(void)
{
        gen('(');

        gen_rand_expr();

        gen(' ');
        gen(gen_rand_op());
        gen(' ');

        gen_rand_expr();

        gen(')');
}

static void gen_rand_expr(void)
{
        if (current_depth >= MAX_DEPTH) {
                gen_num();
                return;
        }

        current_depth++;

        switch (choose(2)) {
        case 0:
                gen_num();
                break;
        case 1:
                gen_binary_expr();
                break;
        }

        current_depth--;
}

int main(int argc, char *argv[])
{
        int seed = time(0);
        srand(seed);
        int loop = 1;
        if (argc > 1) {
                sscanf(argv[1], "%d", &loop);
        }
        int i;
        for (i = 0; i < loop; i++) {
                pos = 0;
                current_depth = 0;
                gen_rand_expr();

                sprintf(code_buf, code_format, buf);

                FILE *fp = fopen("/tmp/.code.c", "w");
                assert(fp != NULL);
                fputs(code_buf, fp);
                fclose(fp);

                int ret = system("gcc -O2 -Werror=div-by-zero /tmp/.code.c -o /tmp/.expr 2>/dev/null");
                if (ret != 0)
                        continue;

                fp = popen("/tmp/.expr", "r");
                assert(fp != NULL);

                int result;
                ret = fscanf(fp, "%d", &result);
                int status = pclose(fp);

                if (status != 0 || ret != 1)
                        continue;

                printf("%u %s\n", result, buf);
        }
        return 0;
}
