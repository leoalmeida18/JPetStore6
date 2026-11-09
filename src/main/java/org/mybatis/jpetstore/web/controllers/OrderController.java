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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpSession;

import org.mybatis.jpetstore.domain.Cart;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Class OrderController.
 */
@Controller
@RequestMapping("/order")
public class OrderController {

  /** The new order view. */
  private static final String NEW_ORDER_VIEW = "order/NewOrderForm";
  /** The shipping view. */
  private static final String SHIPPING_VIEW = "order/ShippingForm";
  /** The confirm view. */
  private static final String CONFIRM_VIEW = "order/ConfirmOrder";
  /** The view order view. */
  private static final String VIEW_ORDER_VIEW = "order/ViewOrder";
  /** The list orders view. */
  private static final String LIST_ORDERS_VIEW = "order/ListOrders";
  /** The error view. */
  private static final String ERROR_VIEW = "common/Error";
  /** Redirect used when an order page requires a signed-on user. */
  private static final String REDIRECT_TO_SIGNON = "redirect:/account";

  /** Message shown when an order page requires a signed-on user. */
  private static final String SIGNON_REQUIRED_MESSAGE = "You must sign on before attempting to check out. Please sign on and try checking out again.";

  /** Message shown when the payment form is submitted without card number or expiry date. */
  private static final String PAYMENT_DETAILS_REQUIRED_MESSAGE = "Please enter the card number and expiry date.";

  /** Session key of the signed-on account (shared with AccountController and the JSPs). */
  private static final String SESSION_ACCOUNT = "accountBean";
  /** Session key of the shopping cart. */
  private static final String SESSION_CART = "cart";
  /** Session key of the order being checked out. */
  private static final String SESSION_ORDER = "order";

  /** The card type list. */
  private static final List<String> CARD_TYPE_LIST = Collections
      .unmodifiableList(Arrays.asList("Visa", "MasterCard", "American Express"));

  /** The order service. */
  private final OrderService orderService;

  /**
   * Instantiates a new order controller.
   *
   * @param orderService
   *          the order service
   */
  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * Finds the signed-on account in the session. Single place for the sign-on check used by every order page.
   *
   * @param session
   *          the session
   *
   * @return the account session, or empty when nobody is signed on
   */
  private static Optional<AccountController.AccountSession> findSignedOnAccount(HttpSession session) {
    AccountController.AccountSession accountSession = (AccountController.AccountSession) session
        .getAttribute(SESSION_ACCOUNT);
    if (accountSession == null || !accountSession.isAuthenticated()) {
      return Optional.empty();
    }
    return Optional.of(accountSession);
  }

  /**
   * List orders.
   *
   * @param session
   *          the session
   * @param model
   *          the model
   *
   * @return the string
   */
  @GetMapping("/list")
  public String listOrders(HttpSession session, Model model) {
    Optional<AccountController.AccountSession> accountSession = findSignedOnAccount(session);
    if (!accountSession.isPresent()) {
      model.addAttribute("message", SIGNON_REQUIRED_MESSAGE);
      return ERROR_VIEW;
    }
    List<Order> orderList = orderService.getOrdersByUsername(accountSession.get().getAccount().getUsername());
    model.addAttribute("orderList", orderList);
    return LIST_ORDERS_VIEW;
  }

  /**
   * New order form.
   *
   * @param session
   *          the session
   * @param model
   *          the model
   *
   * @return the string
   */
  @GetMapping("/new")
  public String newOrderForm(HttpSession session, Model model) {
    Optional<AccountController.AccountSession> accountSession = findSignedOnAccount(session);
    Cart cart = (Cart) session.getAttribute(SESSION_CART);

    if (!accountSession.isPresent()) {
      model.addAttribute("message", SIGNON_REQUIRED_MESSAGE);
      return REDIRECT_TO_SIGNON;
    }
    if (cart != null) {
      Order order = new Order();
      order.initOrder(accountSession.get().getAccount(), cart);
      session.setAttribute(SESSION_ORDER, order);
      model.addAttribute("order", order);
      model.addAttribute("creditCardTypes", CARD_TYPE_LIST);
      return NEW_ORDER_VIEW;
    }
    model.addAttribute("message", "An order could not be created because a cart could not be found.");
    return ERROR_VIEW;
  }

  /**
   * New order.
   *
   * @param order
   *          the order
   * @param shippingAddressRequired
   *          the shipping address required
   * @param confirmed
   *          the confirmed
   * @param session
   *          the session
   * @param model
   *          the model
   *
   * @return the string
   */
  @PostMapping("/new")
  public String newOrder(@ModelAttribute Order order,
      @RequestParam(value = "shippingAddressRequired", defaultValue = "false") boolean shippingAddressRequired,
      @RequestParam(value = "confirmed", defaultValue = "false") boolean confirmed, HttpSession session, Model model) {

    Order sessionOrder = (Order) session.getAttribute(SESSION_ORDER);

    // The confirm form only posts confirmed=true, so the bound order is empty: use the session order.
    if (confirmed) {
      return confirmOrder(sessionOrder, session, model);
    }

    // Each checkout step posts only part of the order; merge it into the order kept in the session.
    Order currentOrder = order;
    if (sessionOrder != null) {
      sessionOrder.updateFrom(order);
      currentOrder = sessionOrder;
    }

    session.setAttribute(SESSION_ORDER, currentOrder);
    model.addAttribute("order", currentOrder);
    if (!currentOrder.hasPaymentDetails()) {
      return askForPaymentDetails(model);
    }
    return shippingAddressRequired ? SHIPPING_VIEW : CONFIRM_VIEW;
  }

  /**
   * Sends the customer back to the payment form when the card number or the expiry date is missing.
   *
   * @param model
   *          the model, already holding the order being checked out
   *
   * @return the string
   */
  private String askForPaymentDetails(Model model) {
    model.addAttribute("message", PAYMENT_DETAILS_REQUIRED_MESSAGE);
    model.addAttribute("creditCardTypes", CARD_TYPE_LIST);
    return NEW_ORDER_VIEW;
  }

  /**
   * Saves the order kept in the session and empties the cart.
   *
   * @param sessionOrder
   *          the order being checked out, or null when the session has none
   * @param session
   *          the session
   * @param model
   *          the model
   *
   * @return the string
   */
  private String confirmOrder(Order sessionOrder, HttpSession session, Model model) {
    if (sessionOrder == null) {
      model.addAttribute("message", "An error occurred processing your order (order was null).");
      return ERROR_VIEW;
    }
    if (!sessionOrder.hasPaymentDetails()) {
      model.addAttribute("order", sessionOrder);
      return askForPaymentDetails(model);
    }
    orderService.insertOrder(sessionOrder);
    session.setAttribute(SESSION_CART, new Cart());
    model.addAttribute("message", "Thank you, your order has been submitted.");
    model.addAttribute("order", sessionOrder);
    return VIEW_ORDER_VIEW;
  }

  /**
   * View order.
   *
   * @param orderId
   *          the order id
   * @param session
   *          the session
   * @param model
   *          the model
   *
   * @return the string
   */
  @GetMapping("/view")
  public String viewOrder(@RequestParam("orderId") int orderId, HttpSession session, Model model) {
    Optional<AccountController.AccountSession> accountSession = findSignedOnAccount(session);
    if (!accountSession.isPresent()) {
      return REDIRECT_TO_SIGNON;
    }
    Order order = orderService.getOrder(orderId);
    if (order == null) {
      model.addAttribute("message", "Order not found.");
      return ERROR_VIEW;
    }
    if (accountSession.get().getAccount().getUsername().equals(order.getUsername())) {
      model.addAttribute("order", order);
      return VIEW_ORDER_VIEW;
    } else {
      model.addAttribute("message", "You may only view your own orders.");
      return ERROR_VIEW;
    }
  }

}
