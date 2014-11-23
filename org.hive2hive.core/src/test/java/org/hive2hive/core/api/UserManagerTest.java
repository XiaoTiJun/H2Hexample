package org.hive2hive.core.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.api.interfaces.IH2HNode;
import org.hive2hive.core.api.interfaces.IUserManager;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.utils.NetworkTestUtil;
import org.hive2hive.core.utils.helper.TestFileAgent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserManagerTest extends H2HJUnitTest {

	private static List<IH2HNode> network;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = UserManagerTest.class;
		beforeClass();
	}

	@AfterClass
	public static void endTest() {
		afterClass();
	}

	@Before
	public void before() {
		beforeMethod();
		network = NetworkTestUtil.createH2HNetwork(5);
	}

	@After
	public void after() {
		NetworkTestUtil.shutdownH2HNetwork(network);
		afterMethod();
	}

	@Test
	public void isRegisteredTest() throws NoPeerConnectionException, InterruptedException {
		UserCredentials userCredentials = generateRandomCredentials();
		String userId = userCredentials.getUserId();

		// all nodes must have same result: false
		for (int i = 0; i < network.size(); i++) {
			boolean isRegistered = network.get(i).getUserManager().isRegistered(userId);
			assertFalse(isRegistered);
		}

		// registering from random node (await)
		IUserManager userManager = network.get(new Random().nextInt(network.size())).getUserManager();
		userManager.configureAutostart(true);
		userManager.createRegisterProcess(userCredentials).await();

		// all nodes must have same result: true
		for (int i = 0; i < network.size(); i++) {
			boolean isRegistered = network.get(i).getUserManager().isRegistered(userId);
			assertTrue(isRegistered);
		}

		// TODO test after unregistering
	}

	@Test
	public void isLoggedInTest() throws NoPeerConnectionException, InterruptedException, NoSessionException {
		UserCredentials userCredentials = generateRandomCredentials();
		String userId = userCredentials.getUserId();

		TestFileAgent fileAgent = new TestFileAgent();

		// all nodes must have same result: false
		for (int i = 0; i < network.size(); i++) {
			boolean isLoggedIn = network.get(i).getUserManager().isLoggedIn(userId);
			assertFalse(isLoggedIn);
		}

		// before registering: login all nodes and check again
		for (int i = 0; i < network.size(); i++) {
			network.get(i).getUserManager().configureAutostart(true);
			network.get(i).getUserManager().createLoginProcess(userCredentials, fileAgent).await();
			boolean isLoggedIn = network.get(i).getUserManager().isLoggedIn(userId);
			assertFalse(isLoggedIn);
		}

		// registering from random node (await)
		network.get(new Random().nextInt(network.size())).getUserManager().createRegisterProcess(userCredentials).await();

		// after registering: login all nodes and check again
		for (int i = 0; i < network.size(); i++) {
			network.get(i).getUserManager().createLoginProcess(userCredentials, fileAgent).await();
			boolean isLoggedIn = network.get(i).getUserManager().isLoggedIn(userId);
			assertTrue(isLoggedIn);
		}

		// logout
		for (int i = 0; i < network.size(); i++) {
			network.get(i).getUserManager().createLogoutProcess().await();
			boolean isLoggedIn = network.get(i).getUserManager().isLoggedIn(userId);
			assertFalse(isLoggedIn);
		}

		// TODO test after unregister
	}
}
