package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.customer.adapter.out.security.CustomerEmailBlindIndex;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CustomerPiiReadSupportTest {

    @Mock
    private ObjectProvider<CustomerPiiCrypto> cryptoProvider;

    @Test
    void plaintextModeReturnsStoredValues() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(false, cryptoProvider);

        support.validateConfiguration();

        assertThat(support.encryptedReads()).isFalse();
        assertThat(support.email("user@example.com")).isEqualTo("user@example.com");
        assertThat(support.phoneNumber("01012345678")).isEqualTo("01012345678");
    }

    @Test
    void encryptedModeRequiresCryptoConfiguration() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        assertThatThrownBy(support::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("키 설정이 필요합니다");
    }

    @Test
    void encryptedModeRejectsMissingEmailCiphertext() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        assertThatThrownBy(() -> support.email(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일 암호문이 누락");
    }

    @Test
    void encryptedModeRejectsMissingPhoneCiphertext() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        assertThatThrownBy(() -> support.phoneNumber(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("휴대폰 암호문이 누락");
    }

    @Test
    void plaintextModeRejectsHashSearch() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(false, cryptoProvider);

        assertThatThrownBy(() -> support.emailHash("user@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("암호화 조회 모드");
    }

    @Test
    void encryptedModeRejectsNullHashInput() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        assertThatThrownBy(() -> support.emailHash(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email must not be null");
    }

    @Test
    void encryptedModeFailsWithoutCryptoBean() {
        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        assertThatThrownBy(() -> support.email("v1:invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("암호화 설정이 누락");
    }

    @Test
    void encryptedModeDecryptsAndHashesSuccessfully() {
        byte[] aesKey = new byte[32];
        byte[] hmacKey = new byte[32];

        Arrays.fill(aesKey, (byte) 1);
        Arrays.fill(hmacKey, (byte) 2);

        CustomerPiiCrypto crypto =
                new CustomerPiiCrypto(new CustomerPiiCipher(aesKey), new CustomerEmailBlindIndex(hmacKey));

        when(cryptoProvider.getIfAvailable()).thenReturn(crypto);

        CustomerPiiReadSupport support = new CustomerPiiReadSupport(true, cryptoProvider);

        support.validateConfiguration();

        String email = "user@example.com";
        String phone = "01012345678";

        String encryptedEmail = crypto.cipher().encrypt(email, EMAIL);
        String encryptedPhone = crypto.cipher().encrypt(phone, PHONE_NUMBER);

        assertThat(support.encryptedReads()).isTrue();
        assertThat(support.email(encryptedEmail)).isEqualTo(email);
        assertThat(support.phoneNumber(encryptedPhone)).isEqualTo(phone);

        assertThat(support.emailHash(email)).isEqualTo(crypto.emailBlindIndex().hash(email));
    }
}
