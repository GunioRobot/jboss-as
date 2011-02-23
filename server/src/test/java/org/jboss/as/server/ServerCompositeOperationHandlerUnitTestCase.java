/**
 *
 */
package org.jboss.as.server;

import static org.jboss.as.server.ServerModelControllerImplUnitTestCase.DESC_PROVIDER;
import static org.jboss.as.server.ServerModelControllerImplUnitTestCase.NULL_REPO;
import static org.jboss.as.server.ServerModelControllerImplUnitTestCase.createTestNode;
import static org.jboss.as.server.ServerModelControllerImplUnitTestCase.getOperation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.BaseCompositeOperationHandler;
import org.jboss.as.server.ServerModelControllerImplUnitTestCase.BadHandler;
import org.jboss.as.server.ServerModelControllerImplUnitTestCase.EvilHandler;
import org.jboss.as.server.ServerModelControllerImplUnitTestCase.GoodHandler;
import org.jboss.as.server.ServerModelControllerImplUnitTestCase.HandleFailedHandler;
import org.jboss.as.server.ServerModelControllerImplUnitTestCase.NullConfigurationPersister;
import org.jboss.as.server.operations.ServerCompositeOperationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link BaseCompositeOperationHandler}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerCompositeOperationHandlerUnitTestCase {

    private TestModelController controller;

    private static final AtomicBoolean runtimeState = new AtomicBoolean(true);

    @Before
    public void setupController() {
        ServiceContainer container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        controller = new TestModelController(container, target);
        runtimeState.set(true);
    }

    @Test
    public void testGoodCompositeExecution() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("good", "attr2", 1);
        ModelNode result = controller.execute(getCompositeOperation(null, step1, step2));
        assertEquals("success", result.get("outcome").asString());
        assertEquals(2, result.get("result").asInt());
        assertEquals("success", result.get("result").get(0).get("outcome").asString());
        assertEquals("success", result.get("result").get(1).get("outcome").asString());
        assertEquals(1, result.get("result").get(0).get("result").asInt());
        assertEquals(2, result.get("result").get(1).get("result").asInt());
        assertEquals("good", result.get("result").get(0).get("compensating-operation", "operation").asString());
        assertEquals("good", result.get("result").get(1).get("compensating-operation", "operation").asString());
        assertEquals(new ModelNode().setEmptyList(), result.get("result").get(0).get("compensating-operation", "address"));
        assertEquals(new ModelNode().setEmptyList(), result.get("result").get(1).get("compensating-operation", "address"));
        assertEquals("composite", result.get("compensating-operation", "operation").asString());
        assertEquals(new ModelNode().setEmptyList(), result.get("compensating-operation", "address"));
        assertEquals(2, result.get("compensating-operation", "steps").asInt());

        // 2 ops set it from true to false to true
        assertTrue(runtimeState.get());

        assertEquals(2, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(1, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testOperationFailedExecution() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("bad", "attr2", 1);
        try {
            controller.execute(getCompositeOperation(null, step1, step2));
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("this request is bad") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(1, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(2, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testOperationFailedExecutionNoRollback() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("bad", "attr2", 1);
        try {
            ModelNode op = getCompositeOperation(null, step1, step2);
            op.get("rollback-on-runtime-failure").set(false);
            controller.execute(op);
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("this request is bad") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(2, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(1, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testUnhandledFailureExecution() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("evil", "attr2", 1);
        try {
            controller.execute(getCompositeOperation(null, step1, step2));
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("this handler is evil") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(1, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(2, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testUnhandledFailureExecutionNoRollback() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("evil", "attr2", 1);
        try {
            ModelNode op = getCompositeOperation(null, step1, step2);
            op.get("rollback-on-runtime-failure").set(false);
            controller.execute(op);
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("this handler is evil") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(2, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(1, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testHandleFailedExecution() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("handleFailed", "attr2", 1);
        try {
            controller.execute(getCompositeOperation(null, step1, step2));
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("handleFailed") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(1, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(2, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    @Test
    public void testHandleFailedExecutionNoRollback() throws Exception {
        ModelNode step1 = getOperation("good", "attr1", 2);
        ModelNode step2 = getOperation("handleFailed", "attr2", 1);
        try {
            ModelNode op = getCompositeOperation(null, step1, step2);
            op.get("rollback-on-runtime-failure").set(false);
            controller.execute(op);
            fail("should have thrown OperationFailedException");
        }
        catch (OperationFailedException e) {
            assertTrue(e.getFailureDescription().get("failure-description").toString().indexOf("handleFailed") > - 1);
        }

        assertTrue(runtimeState.get());

        assertEquals(2, controller.execute(getOperation("good", "attr1", 3)).get("result").asInt());
        assertEquals(1, controller.execute(getOperation("good", "attr2", 3)).get("result").asInt());
    }

    public static ModelNode getCompositeOperation(Boolean rollback, ModelNode... steps) {

        ModelNode op = new ModelNode();
        op.get("operation").set("composite");
        op.get("address").setEmptyList();
        for (ModelNode step : steps) {
            op.get("steps").add(step);
        }
        if (rollback != null) {
            op.get("rollback-on-runtime-failure").set(rollback);
        }
        return op;
    }

    private static class TestModelController extends ServerControllerImpl {
        protected TestModelController(ServiceContainer container, ServiceTarget target) {
            super(container, target, null, new NullConfigurationPersister(), NULL_REPO , Executors.newCachedThreadPool());

            getModel().set(createTestNode());

            getRegistry().registerOperationHandler("good", new GoodHandler(), DESC_PROVIDER, false);
            getRegistry().registerOperationHandler("bad", new BadHandler(), DESC_PROVIDER, false);
            getRegistry().registerOperationHandler("evil", new EvilHandler(), DESC_PROVIDER, false);
            getRegistry().registerOperationHandler("handleFailed", new HandleFailedHandler(), DESC_PROVIDER, false);

            getRegistry().registerOperationHandler(ServerCompositeOperationHandler.OPERATION_NAME, ServerCompositeOperationHandler.INSTANCE, DESC_PROVIDER, false);
        }
    }
}