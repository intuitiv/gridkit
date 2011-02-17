package com.medx.proxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import com.medx.attribute.AttrKeyRegistry;
import com.medx.proxy.handler.CachedMethodHandlerFactory;
import com.medx.proxy.handler.MethodHandlerFactory;
import com.medx.proxy.test.TestAttrKeyRegistry;
import com.medx.proxy.test.TestDictionary;
import com.medx.proxy.test.TestTypeRegistry;
import com.medx.proxy.test.model.Customer;
import com.medx.proxy.test.model.Order;
import com.medx.proxy.test.model.OrderItem;
import com.medx.type.TypeRegistry;

public class MapProxyImplTest {
	private static final double DELTA = 0.0001;
	
	private static TypeRegistry typeRegistry = new TestTypeRegistry();
	private static AttrKeyRegistry attrKeyRegistry = new TestAttrKeyRegistry();
	private static MethodHandlerFactory methodHandlerFactory = new CachedMethodHandlerFactory(attrKeyRegistry);
	private static MapProxyFactory proxyFactory = new MapProxyFactoryImpl(typeRegistry, methodHandlerFactory);
	
	public static Map<Integer, Object> customerMap = null;
	public static Map<Integer, Object> orderMap = null;
	
	public static Map<Integer, Object> orderItem1Map = null;
	public static Map<Integer, Object> orderItem2Map = null;
	public static Map<Integer, Object> orderItem3Map = null;
	
	@Before
	public void before() {
		customerMap = new HashMap<Integer, Object>();
		
		int[] customerClasses = {0};
		customerMap.put(Integer.MIN_VALUE, customerClasses);
		customerMap.put(TestDictionary.Id.customerName, "Ted");
		
		orderMap = new HashMap<Integer, Object>();
		
		int[] orderClasses = {1};
		orderMap.put(Integer.MIN_VALUE, orderClasses);
		orderMap.put(TestDictionary.Id.orderId, 0);
		orderMap.put(TestDictionary.Id.orderCustomer, customerMap);
		
		int[] orderIteamClasses = {2};
		
		orderItem1Map = new HashMap<Integer, Object>(); 
		
		orderItem1Map.put(Integer.MIN_VALUE, orderIteamClasses);
		orderItem1Map.put(TestDictionary.Id.orderItemTitle, "clock");
		orderItem1Map.put(TestDictionary.Id.orderItemPrice, 1.0);
		
		orderItem2Map = new HashMap<Integer, Object>(); 
		
		orderItem2Map.put(Integer.MIN_VALUE, orderIteamClasses);
		orderItem2Map.put(TestDictionary.Id.orderItemTitle, "mouse");
		orderItem2Map.put(TestDictionary.Id.orderItemPrice, 2.0);
		
		orderItem3Map = new HashMap<Integer, Object>(); 
		
		orderItem3Map.put(Integer.MIN_VALUE, orderIteamClasses);
		orderItem3Map.put(TestDictionary.Id.orderItemTitle, "keyboard");
		orderItem3Map.put(TestDictionary.Id.orderItemPrice, 3.0);
		
		List<Map<Integer, Object>> orderItems = new ArrayList<Map<Integer,Object>>();
		
		orderItems.add(orderItem1Map);
		orderItems.add(orderItem2Map);
		
		orderMap.put(TestDictionary.Id.orderItems, orderItems);
	}
	
	@Test
	public void test1() {
		Customer customer = proxyFactory.createMapProxy(customerMap);
		assertEquals("Ted", customer.getName());
		customer.setName("Ralph");
		assertEquals("Ralph", customer.getName());
	}
	
	@Test
	public void test2() {
		Order order = proxyFactory.createMapProxy(orderMap);
		assertEquals("Ted", order.getCustomer().getName());
		order.getCustomer().setName("Ralph");
		assertEquals("Ralph", order.getCustomer().getName());
	}
	
	@Test
	public void test3() {
		Order order = proxyFactory.createMapProxy(orderMap);
		assertEquals(0, order.getId());
		order.setId(1);
		assertEquals(1, order.getId());
	}
	
	@Test
	public void test4() {
		Order order = proxyFactory.createMapProxy(orderMap);
		
		assertEquals("clock", order.getItems().get(0).getTitle());
		assertEquals(1.0, order.getItems().get(0).getPrice(), DELTA);
		
		assertEquals("mouse", order.getItems().get(1).getTitle());
		assertEquals(2.0, order.getItems().get(1).getPrice(), DELTA);
		
		order.getItems().get(0).setTitle("clock!");
		order.getItems().get(0).setPrice(-1.0);
		
		order.getItems().get(1).setTitle("mouse!");
		order.getItems().get(1).setPrice(-2.0);
		
		assertEquals("clock!", order.getItems().get(0).getTitle());
		assertEquals(-1.0, order.getItems().get(0).getPrice(), DELTA);
		
		assertEquals("mouse!", order.getItems().get(1).getTitle());
		assertEquals(-2.0, order.getItems().get(1).getPrice(), DELTA);
	}
	
	@Test
	public void test5() {
		Order order = proxyFactory.createMapProxy(orderMap);
		OrderItem orderItem = proxyFactory.createMapProxy(orderItem3Map);
		
		order.setItems(Collections.singletonList(orderItem));

		assertEquals(1, order.getItems().size());
		assertEquals("keyboard", order.getItems().get(0).getTitle());
		assertEquals(3.0, order.getItems().get(0).getPrice(), DELTA);
	}
}
