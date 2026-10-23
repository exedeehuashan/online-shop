package com.example.onlineshop.catalog.domain;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test;
class ProductTest {
 @Test void acceptsExactMoney(){var p=new Product(UUID.randomUUID(),"Tea","Synthetic",new BigDecimal("12.30"),"NZD",3,Instant.EPOCH,Instant.EPOCH);assertThat(p.price().toPlainString()).isEqualTo("12.30");}
 @Test void rejectsInvalidValues(){assertThatThrownBy(()->new Product(UUID.randomUUID()," ","",new BigDecimal("-1"),"NZD",0,Instant.EPOCH,Instant.EPOCH)).isInstanceOf(ProductValidationException.class);}
}
