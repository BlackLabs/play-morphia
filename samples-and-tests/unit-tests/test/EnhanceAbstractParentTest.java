import models.ConcreteChild;

import org.junit.Test;

import play.test.UnitTest;

/**
 * Blob from parent should be available to child
 *
 */
public class EnhanceAbstractParentTest extends UnitTest {

	@Test
	public void testCreateConcreteChild() {
		ConcreteChild child = new ConcreteChild();
	}
}
