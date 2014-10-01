/**
 * Originally from http://www.cornetdesign.com/files/BeanTestCase.java.txt
 */
package org.connectbot.mock;

import junit.framework.TestCase;

import java.lang.reflect.Field;

public class BeanTestCase extends TestCase {

	private static final String TEST_STRING_VAL1 = "Some Value";
	private static final String TEST_STRING_VAL2 = "Some Other Value";

	public static void assertMeetsEqualsContract(Class<?> classUnderTest,
			String[] fieldNames) {
		Object o1;
		Object o2;
		try {
			// Get Instances
			o1 = classUnderTest.newInstance();
			o2 = classUnderTest.newInstance();

			assertTrue(
					"Instances with default constructor not equal (o1.equals(o2))",
					o1.equals(o2));
			assertTrue(
					"Instances with default constructor not equal (o2.equals(o1))",
					o2.equals(o1));

			Field[] fields = getFieldsByNameOrAll(classUnderTest, fieldNames);

			for (int i = 0; i < fields.length; i++) {

				// Reset the instances
				o1 = classUnderTest.newInstance();
				o2 = classUnderTest.newInstance();

				Field field = fields[i];
				field.setAccessible(true);
				if (field.getType() == String.class) {
					field.set(o1, TEST_STRING_VAL1);
				} else if (field.getType() == boolean.class) {
					field.setBoolean(o1, true);
				} else if (field.getType() == short.class) {
					field.setShort(o1, (short) 1);
				} else if (field.getType() == long.class) {
					field.setLong(o1, (long) 1);
				} else if (field.getType() == float.class) {
					field.setFloat(o1, (float) 1);
				} else if (field.getType() == int.class) {
					field.setInt(o1, 1);
				} else if (field.getType() == byte.class) {
					field.setByte(o1, (byte) 1);
				} else if (field.getType() == char.class) {
					field.setChar(o1, (char) 1);
				} else if (field.getType() == double.class) {
					field.setDouble(o1, (double) 1);
				} else if (field.getType().isEnum()) {
					field.set(o1, field.getType().getEnumConstants()[0]);
				} else if (Object.class.isAssignableFrom(field.getType())) {
					field.set(o1, field.getType().newInstance());
				} else {
					fail("Don't know how to set a " + field.getType().getName());
				}

				assertFalse("Instances with o1 having " + field.getName()
						+ " set and o2 having it not set are equal", o1
						.equals(o2));

				field.set(o2, field.get(o1));

				assertTrue(
						"After setting o2 with the value of the object in o1, the two objects in the field are not equal",
						field.get(o1).equals(field.get(o2)));

				assertTrue(
						"Instances with o1 having "
								+ field.getName()
								+ " set and o2 having it set to the same object of type "
								+ field.get(o2).getClass().getName()
								+ " are not equal", o1.equals(o2));

				if (field.getType() == String.class) {
					field.set(o2, TEST_STRING_VAL2);
				} else if (field.getType() == boolean.class) {
					field.setBoolean(o2, false);
				} else if (field.getType() == short.class) {
					field.setShort(o2, (short) 0);
				} else if (field.getType() == long.class) {
					field.setLong(o2, (long) 0);
				} else if (field.getType() == float.class) {
					field.setFloat(o2, (float) 0);
				} else if (field.getType() == int.class) {
					field.setInt(o2, 0);
				} else if (field.getType() == byte.class) {
					field.setByte(o2, (byte) 0);
				} else if (field.getType() == char.class) {
					field.setChar(o2, (char) 0);
				} else if (field.getType() == double.class) {
					field.setDouble(o2, (double) 1);
				} else if (field.getType().isEnum()) {
					field.set(o2, field.getType().getEnumConstants()[1]);
				} else if (Object.class.isAssignableFrom(field.getType())) {
					field.set(o2, field.getType().newInstance());
				} else {
					fail("Don't know how to set a " + field.getType().getName());
				}
				if (field.get(o1).equals(field.get(o2))) {
					// Even though we have different instances, they are equal.
					// Let's walk one of them
					// to see if we can find a field to set
					Field[] paramFields = field.get(o1).getClass()
							.getDeclaredFields();
					for (int j = 0; j < paramFields.length; j++) {
						paramFields[j].setAccessible(true);
						if (paramFields[j].getType() == String.class) {
							paramFields[j].set(field.get(o1), TEST_STRING_VAL1);
						}
					}
				}

				assertFalse(
						"After setting o2 with a different object than what is in o1, the two objects in the field are equal. "
								+ "This is after an attempt to walk the fields to make them different",
						field.get(o1).equals(field.get(o2)));
				assertFalse(
						"Instances with o1 having "
								+ field.getName()
								+ " set and o2 having it set to a different object are equal",
						o1.equals(o2));
			}

		} catch (InstantiationException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to construct an instance of the class under test");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to construct an instance of the class under test");
		} catch (SecurityException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to read the field from the class under test");
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to find field in the class under test");
		}
	}

	/**
	 * @param classUnderTest
	 * @param fieldNames
	 * @return
	 * @throws NoSuchFieldException
	 */
	private static Field[] getFieldsByNameOrAll(Class<?> classUnderTest,
			String[] fieldNames) throws NoSuchFieldException {
		Field fields[];
		if (fieldNames == null) {
			fields = classUnderTest.getDeclaredFields();
		} else {
			fields = new Field[fieldNames.length];
			for (int i = 0; i < fieldNames.length; i++)
				fields[i] = classUnderTest.getDeclaredField(fieldNames[i]);
		}
		return fields;
	}

	public static void assertMeetsHashCodeContract(Class<?> classUnderTest,
			String[] fieldNames) {
		try {
			Field[] fields = getFieldsByNameOrAll(classUnderTest, fieldNames);

			for (int i = 0; i < fields.length; i++) {
				Object o1 = classUnderTest.newInstance();
				int initialHashCode = o1.hashCode();

				Field field = fields[i];
				field.setAccessible(true);
				if (field.getType() == String.class) {
					field.set(o1, TEST_STRING_VAL1);
				} else if (field.getType() == boolean.class) {
					field.setBoolean(o1, true);
				} else if (field.getType() == short.class) {
					field.setShort(o1, (short) 1);
				} else if (field.getType() == long.class) {
					field.setLong(o1, (long) 1);
				} else if (field.getType() == float.class) {
					field.setFloat(o1, (float) 1);
				} else if (field.getType() == int.class) {
					field.setInt(o1, 1);
				} else if (field.getType() == byte.class) {
					field.setByte(o1, (byte) 1);
				} else if (field.getType() == char.class) {
					field.setChar(o1, (char) 1);
				} else if (field.getType() == double.class) {
					field.setDouble(o1, (double) 1);
				} else if (field.getType().isEnum()) {
					field.set(o1, field.getType().getEnumConstants()[0]);
				} else if (Object.class.isAssignableFrom(field.getType())) {
					field.set(o1, field.getType().newInstance());
				} else {
					fail("Don't know how to set a " + field.getType().getName());
				}
				int updatedHashCode = o1.hashCode();
				assertFalse(
						"The field "
								+ field.getName()
								+ " was not taken into account for the hashCode contract ",
						initialHashCode == updatedHashCode);
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to construct an instance of the class under test");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to construct an instance of the class under test");
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			throw new AssertionError(
					"Unable to find field in the class under test");
		}
	}
}
