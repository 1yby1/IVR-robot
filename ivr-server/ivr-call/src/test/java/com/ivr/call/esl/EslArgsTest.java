package com.ivr.call.esl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EslArgsTest {

    @Test
    void sanitizeText_replacesPipesAndControlChars() {
        assertThat(EslArgs.sanitizeText("hello|world\nfoo\tbar")).isEqualTo("hello world foo bar");
    }

    @Test
    void sanitizeText_returnsEmptyForBlank() {
        assertThat(EslArgs.sanitizeText(null)).isEmpty();
        assertThat(EslArgs.sanitizeText("   ")).isEmpty();
    }

    @Test
    void sanitizeText_keepsChinese() {
        assertThat(EslArgs.sanitizeText("您好，请按 1")).isEqualTo("您好，请按 1");
    }

    @Test
    void sanitizeTarget_acceptsCommonNumberFormats() {
        assertThat(EslArgs.sanitizeTarget("1000")).isEqualTo("1000");
        assertThat(EslArgs.sanitizeTarget("+8613800000000")).isEqualTo("+8613800000000");
        assertThat(EslArgs.sanitizeTarget("agent@10.0.0.1")).isEqualTo("agent@10.0.0.1");
        assertThat(EslArgs.sanitizeTarget("user.name@host:5060")).isEqualTo("user.name@host:5060");
    }

    @Test
    void sanitizeTarget_rejectsSpacesAndShellMetachars() {
        assertThat(EslArgs.sanitizeTarget("1000 && rm -rf /")).isNull();
        assertThat(EslArgs.sanitizeTarget("1000\nuuid_kill")).isNull();
        assertThat(EslArgs.sanitizeTarget("1000;ls")).isNull();
        assertThat(EslArgs.sanitizeTarget("")).isNull();
        assertThat(EslArgs.sanitizeTarget(null)).isNull();
    }
}
