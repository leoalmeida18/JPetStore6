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
package org.mybatis.jpetstore.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mybatis.jpetstore.domain.Account;
import org.mybatis.jpetstore.domain.Cart;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.service.OrderService;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Testes de caracterizacao do OrderController.
 * <p>
 * Registram o comportamento da versao legada (tag v0-legado) ANTES da refatoracao, inclusive comportamentos
 * questionaveis. Eles cobrem os caminhos de newOrder() que os testes originais nao exercitavam (17 de 32 ramos
 * cobertos, segundo o JaCoCo) e os casos de erro listados no diagnostico da Etapa 2 (D4, D7, D10).
 * <p>
 * Quando um PR de refatoracao mudar um comportamento de proposito (por exemplo, o D10), o teste correspondente deve ser
 * atualizado no mesmo PR, documentando o "Antes vs. Depois".
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Caracterizacao do OrderController (comportamento da versao legada)")
class OrderControllerCharacterizationTest {

  @Mock
  private OrderService orderService;

  @InjectMocks
  private OrderController orderController;

  private HttpSession session;
  private Model model;

  @BeforeEach
  void setUp() {
    session = mock(HttpSession.class);
    model = new ExtendedModelMap();
  }

  private static AccountController.AccountSession sessaoLogada(String username) {
    Account account = new Account();
    account.setUsername(username);
    return new AccountController.AccountSession(account, List.of(), true);
  }

  /** Simula o POST do formulario de pagamento (NewOrderForm): so campos de cobranca e cartao. */
  private static Order pedidoDoFormularioDePagamento() {
    Order order = new Order();
    order.setCardType("MasterCard");
    order.setCreditCard("1234 5678 9012 3456");
    order.setExpiryDate("10/2030");
    order.setBillToFirstName("Maria");
    order.setBillToLastName("Souza");
    order.setBillAddress1("Rua A, 10");
    order.setBillAddress2("Apto 201");
    order.setBillCity("Salvador");
    order.setBillState("BA");
    order.setBillZip("40000-000");
    order.setBillCountry("Brasil");
    return order;
  }

  // ---------------------------------------------------------------------------------------------
  // newOrder(): caminhos de confirmacao
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Confirmar sem pedido na sessao mostra erro e nao grava nada")
  void confirmarSemPedidoNaSessaoMostraErro() {
    when(session.getAttribute("order")).thenReturn(null);

    String view = orderController.newOrder(new Order(), false, true, session, model);

    assertThat(view).isEqualTo("common/Error");
    assertThat(model.asMap()).containsEntry("message", "An error occurred processing your order (order was null).");
    verify(orderService, never()).insertOrder(any());
  }

  @Test
  @DisplayName("Confirmar grava o pedido da sessao e esvazia o carrinho")
  void confirmarGravaPedidoDaSessaoEEsvaziaCarrinho() {
    Order pedidoDaSessao = new Order();
    when(session.getAttribute("order")).thenReturn(pedidoDaSessao);

    String view = orderController.newOrder(new Order(), false, true, session, model);

    assertThat(view).isEqualTo("order/ViewOrder");
    assertThat(model.asMap()).containsEntry("order", pedidoDaSessao);
    verify(orderService).insertOrder(pedidoDaSessao);
    verify(session).setAttribute(eq("cart"), any(Cart.class));
  }

  // ---------------------------------------------------------------------------------------------
  // newOrder(): sem pedido na sessao, o pedido do formulario e usado como esta
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Sem pedido na sessao, usa o pedido do formulario e vai para a confirmacao")
  void semPedidoNaSessaoUsaPedidoDoFormularioEVaiParaConfirmacao() {
    Order pedidoDoFormulario = pedidoDoFormularioDePagamento();

    String view = orderController.newOrder(pedidoDoFormulario, false, false, session, model);

    assertThat(view).isEqualTo("order/ConfirmOrder");
    assertThat(model.asMap()).containsEntry("order", pedidoDoFormulario);
    verify(session).setAttribute("order", pedidoDoFormulario);
  }

  @Test
  @DisplayName("Sem pedido na sessao e com entrega alternativa, vai para o formulario de entrega")
  void semPedidoNaSessaoComEntregaAlternativaVaiParaFormularioDeEntrega() {
    Order pedidoDoFormulario = pedidoDoFormularioDePagamento();

    String view = orderController.newOrder(pedidoDoFormulario, true, false, session, model);

    assertThat(view).isEqualTo("order/ShippingForm");
    verify(session).setAttribute("order", pedidoDoFormulario);
  }

  // ---------------------------------------------------------------------------------------------
  // newOrder(): com pedido na sessao, os campos enviados sao copiados um a um (D7)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Formulario de pagamento copia os 11 campos de cobranca e preserva a entrega")
  void formularioDePagamentoCopiaOsOnzeCamposDeCobranca() {
    Order pedidoDaSessao = new Order();
    pedidoDaSessao.setShipCity("Palo Alto");
    when(session.getAttribute("order")).thenReturn(pedidoDaSessao);
    Order formulario = pedidoDoFormularioDePagamento();

    String view = orderController.newOrder(formulario, true, false, session, model);

    assertThat(view).isEqualTo("order/ShippingForm");
    assertThat(model.asMap()).containsEntry("order", pedidoDaSessao);
    verify(session).setAttribute("order", pedidoDaSessao);

    assertThat(pedidoDaSessao.getCardType()).isEqualTo("MasterCard");
    assertThat(pedidoDaSessao.getCreditCard()).isEqualTo("1234 5678 9012 3456");
    assertThat(pedidoDaSessao.getExpiryDate()).isEqualTo("10/2030");
    assertThat(pedidoDaSessao.getBillToFirstName()).isEqualTo("Maria");
    assertThat(pedidoDaSessao.getBillToLastName()).isEqualTo("Souza");
    assertThat(pedidoDaSessao.getBillAddress1()).isEqualTo("Rua A, 10");
    assertThat(pedidoDaSessao.getBillAddress2()).isEqualTo("Apto 201");
    assertThat(pedidoDaSessao.getBillCity()).isEqualTo("Salvador");
    assertThat(pedidoDaSessao.getBillState()).isEqualTo("BA");
    assertThat(pedidoDaSessao.getBillZip()).isEqualTo("40000-000");
    assertThat(pedidoDaSessao.getBillCountry()).isEqualTo("Brasil");

    // O formulario de pagamento nao envia shipToFirstName, entao a entrega fica como estava
    assertThat(pedidoDaSessao.getShipCity()).isEqualTo("Palo Alto");
  }

  @Test
  @DisplayName("Formulario de entrega sobrescreve todos os campos de entrega, ate com vazio")
  void formularioDeEntregaSobrescreveTodosOsCamposDeEntrega() {
    Order pedidoDaSessao = new Order();
    pedidoDaSessao.setShipCity("Palo Alto");
    pedidoDaSessao.setShipAddress2("MS UCUP02-206");
    when(session.getAttribute("order")).thenReturn(pedidoDaSessao);

    Order formulario = new Order();
    formulario.setShipToFirstName("Maria");

    String view = orderController.newOrder(formulario, false, false, session, model);

    assertThat(view).isEqualTo("order/ConfirmOrder");
    assertThat(pedidoDaSessao.getShipToFirstName()).isEqualTo("Maria");
    // Comportamento atual: basta shipToFirstName vir preenchido para os 8 campos de entrega serem
    // trocados, inclusive pelos que vieram vazios.
    assertThat(pedidoDaSessao.getShipCity()).isNull();
    assertThat(pedidoDaSessao.getShipAddress2()).isNull();
  }

  // ---------------------------------------------------------------------------------------------
  // Erros e checagem de login (D4, D10)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Ver pedido inexistente derruba a pagina com NullPointerException (D10)")
  void verPedidoInexistenteLancaNullPointerException() {
    when(session.getAttribute("accountBean")).thenReturn(sessaoLogada("j2ee"));
    when(orderService.getOrder(999)).thenReturn(null);

    // Comportamento atual, a ser corrigido no PR 5: em vez de uma tela de erro, a requisicao falha.
    assertThrows(NullPointerException.class, () -> orderController.viewOrder(999, session, model));
  }

  @Test
  @DisplayName("Iniciar pedido logado mas sem carrinho mostra erro")
  void iniciarPedidoSemCarrinhoMostraErro() {
    when(session.getAttribute("accountBean")).thenReturn(sessaoLogada("j2ee"));

    String view = orderController.newOrderForm(session, model);

    assertThat(view).isEqualTo("common/Error");
    assertThat(model.asMap()).containsEntry("message",
        "An order could not be created because a cart could not be found.");
  }

  @Test
  @DisplayName("Lista de pedidos com sessao nao autenticada mostra erro e nao consulta o banco")
  void listaDePedidosComSessaoNaoAutenticadaMostraErro() {
    Account account = new Account();
    account.setUsername("j2ee");
    when(session.getAttribute("accountBean"))
        .thenReturn(new AccountController.AccountSession(account, List.of(), false));

    String view = orderController.listOrders(session, model);

    assertThat(view).isEqualTo("common/Error");
    verify(orderService, never()).getOrdersByUsername(any());
  }

  @Test
  @DisplayName("Sem login, cada tela de pedido reage de um jeito diferente (D4)")
  void semLoginCadaTelaReageDeUmJeito() {
    // A mesma checagem esta copiada em 3 metodos, com destinos diferentes.
    assertThat(orderController.listOrders(session, model)).isEqualTo("common/Error");
    assertThat(orderController.newOrderForm(session, model)).isEqualTo("redirect:/account");
    assertThat(orderController.viewOrder(1, session, model)).isEqualTo("redirect:/account");
    verify(orderService, never()).getOrder(1);
  }

}
