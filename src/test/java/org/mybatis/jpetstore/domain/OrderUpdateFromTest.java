/*
 *    Copyright 2010-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.jpetstore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de Order.updateFrom(), que recebeu a logica de copia de campos antes espalhada em OrderController.newOrder().
 */
@DisplayName("Order.updateFrom: mescla os campos enviados em cada etapa do checkout")
class OrderUpdateFromTest {

  private static Order pedidoCompleto() {
    Order order = new Order();
    order.setCardType("Visa");
    order.setCreditCard("999 9999 9999 9999");
    order.setExpiryDate("12/03");
    order.setBillToFirstName("ABC");
    order.setBillToLastName("XYX");
    order.setBillAddress1("901 San Antonio Road");
    order.setBillAddress2("MS UCUP02-206");
    order.setBillCity("Palo Alto");
    order.setBillState("CA");
    order.setBillZip("94303");
    order.setBillCountry("USA");
    order.setShipToFirstName("ABC");
    order.setShipToLastName("XYX");
    order.setShipAddress1("901 San Antonio Road");
    order.setShipAddress2("MS UCUP02-206");
    order.setShipCity("Palo Alto");
    order.setShipState("CA");
    order.setShipZip("94303");
    order.setShipCountry("USA");
    return order;
  }

  @Test
  @DisplayName("Formulario vazio nao altera nada")
  void formularioVazioNaoAlteraNada() {
    Order order = pedidoCompleto();

    order.updateFrom(new Order());

    assertThat(order).usingRecursiveComparison().isEqualTo(pedidoCompleto());
  }

  @Test
  @DisplayName("Campos de pagamento enviados substituem os atuais; os ausentes ficam como estao")
  void camposDePagamentoEnviadosSubstituemOsAtuais() {
    Order order = pedidoCompleto();
    Order formulario = new Order();
    formulario.setCardType("MasterCard");
    formulario.setBillCity("Salvador");

    order.updateFrom(formulario);

    assertThat(order.getCardType()).isEqualTo("MasterCard");
    assertThat(order.getBillCity()).isEqualTo("Salvador");
    assertThat(order.getCreditCard()).isEqualTo("999 9999 9999 9999");
    assertThat(order.getBillState()).isEqualTo("CA");
    assertThat(order.getShipCity()).isEqualTo("Palo Alto");
  }

  @Test
  @DisplayName("Formulario de entrega troca os 8 campos de entrega e preserva a cobranca")
  void formularioDeEntregaTrocaOsOitoCampos() {
    Order order = pedidoCompleto();
    Order formulario = new Order();
    formulario.setShipToFirstName("Maria");
    formulario.setShipToLastName("Souza");
    formulario.setShipAddress1("Rua A, 10");
    formulario.setShipCity("Salvador");
    formulario.setShipState("BA");
    formulario.setShipZip("40000-000");
    formulario.setShipCountry("Brasil");

    order.updateFrom(formulario);

    assertThat(order.getShipToFirstName()).isEqualTo("Maria");
    assertThat(order.getShipCity()).isEqualTo("Salvador");
    // Mesmo comportamento da versao legada: o complemento nao enviado fica vazio.
    assertThat(order.getShipAddress2()).isNull();
    assertThat(order.getBillCity()).isEqualTo("Palo Alto");
    assertThat(order.getCardType()).isEqualTo("Visa");
  }

}
