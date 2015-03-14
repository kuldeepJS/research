package graphs.common;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
//can we get rid of all mappings?
//to be refactored with validations.

public class XeroxMachine {
	// source class has cyclic dependencies and for each source class object, we
	// create a dest class object..we have a a mapping here...to avoid cycles.

	private Map<Object, Object> srcDestMap = new HashMap<Object, Object>();
	private Objenesis objectGenesis = new ObjenesisStd();

	public Object deepCopy(Object srcObj, Class<?> destClass) throws Exception {
		Object destObj = newInstance(destClass);
		deepCopy(srcObj, destObj);
		return destObj;
	}

	private Object newInstance(Class<?> destClass) {
		ObjectInstantiator thingyInstantiator = objectGenesis
				.getInstantiatorOf(destClass);
		Object destObj = thingyInstantiator.newInstance();
		return destObj;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void deepCopy(Object srcObj, Object destObj) throws Exception {
		Field[] destFields = getAllFields(destObj);
		Map<String, Field> fieldNameToObjMap = prepareMap(srcObj);
		for (Field destField : destFields) {
			Field srcField = fieldNameToObjMap.get(destField.getName());
			if (srcField == null) {
				System.out.println("That will doesn't happen in our case.");
			}
			if (Modifier.isStatic(srcField.getModifiers())) {
				// continue;
				//additional logic can go here...for now we move on.
			}
			if (!destField.isAccessible()) {
				destField.setAccessible(true);
				srcField.setAccessible(true);
			}
			Object valFieldSrc = srcField.get(srcObj);
			if (valFieldSrc != null) {
				System.out.println("Process :" + valFieldSrc.getClass());
				if (valFieldSrc.getClass().equals(Hashtable.class)) {
					// a risk we are taking with hashtable.
					Field tableField = valFieldSrc.getClass().getDeclaredField(
							"table");
					System.out.println(tableField);
					if (!tableField.isAccessible()) {
						tableField.setAccessible(true);
					}
					Object entryArray[] = (Object[]) tableField
							.get(valFieldSrc);
					System.out.println(entryArray[0]);
					destField.set(destObj, valFieldSrc);

				} else if (Modifier.isStatic(srcField.getModifiers())) {
					// static field to be handled here...
					// same code that follows after the next if.
				} else if (srcDestMap.containsKey(valFieldSrc)) {
					if (destField.getType().equals(Vector.class)) {
						Object obj = srcDestMap.get(valFieldSrc);
						if (obj.getClass().isArray()) {
							Vector vector = new Vector(Array.getLength(obj));
							Class<?> componentType = obj.getClass()
									.getComponentType();
							for (int i = 0; i < vector.size(); i++) {
								Object objI = ((Object[]) obj)[i];
								vector.add(componentType.cast(objI));
							}
							destField.set(destObj, vector);
						}
					} else if (destField.getType().equals(List.class)) {
						Object obj = srcDestMap.get(valFieldSrc);
						if (obj.getClass().isArray()) {
							List vector = new ArrayList(Array.getLength(obj));
							Class<?> componentType = obj.getClass()
									.getComponentType();
							for (int i = 0; i < vector.size(); i++) {
								Object objI = ((Object[]) obj)[i];
								vector.add(componentType.cast(objI));
							}
							destField.set(destObj, vector);
						}
					} else {
						destField.set(destObj, srcDestMap.get(valFieldSrc));
					}
				} else if (valFieldSrc.getClass().isPrimitive()
						|| Number.class
								.isAssignableFrom(valFieldSrc.getClass())
						|| valFieldSrc.getClass() == String.class) {
					destField.set(destObj, valFieldSrc);
				} else if (valFieldSrc.getClass().isArray()) {
					if (Array.getLength(valFieldSrc) != 0) {
						if (valFieldSrc.getClass().getComponentType()
								.isPrimitive()) {
							destField.set(destObj, valFieldSrc);
						} else {
							Object obj = ((Object[]) valFieldSrc)[0];
							if (obj != null) {
								Class<?> tClass = obj.getClass();
								System.out.println(tClass);
								int length = Array.getLength(valFieldSrc);
								Class<?> mClass = getMappedClass(tClass);
								Object destArray = Array.newInstance(mClass,
										length);
								for (int ithVal = 0; ithVal < length; ithVal++) {
									Object valFieldSrcI = Array.get(
											valFieldSrc, ithVal);
									if (valFieldSrcI != null) {
										Class<?> destClass = getMappedClass(tClass);
										System.out.println(destClass);
										if (destClass.isPrimitive()) {
											Array.set(destArray, ithVal,
													valFieldSrcI);
										} else {
											if (srcDestMap
													.containsKey(valFieldSrcI)) {
												Array.set(
														destArray,
														ithVal,
														srcDestMap
																.get(valFieldSrcI));
											} else {
												Object newObject = newInstance(destClass);
												srcDestMap.put(valFieldSrcI,
														newObject);
												deepCopy(valFieldSrcI,
														newObject);
												Array.set(
														destArray,
														ithVal,
														destClass
																.cast(newObject));
											}
										}
									}
								}
								System.out.println(destArray.getClass());
								srcDestMap.put(destObj, (Object[]) destArray);
								destField.set(destObj, (Object[]) destArray);
							}

						}
					}
				} else {
					Object newObject = cloneObject(valFieldSrc);
					destField.set(destObj, newObject);
				}
			}
		}

	}

	private Object cloneObject(Object valFieldSrc) throws Exception {
		Class<?> newClass = getMappedClass(valFieldSrc.getClass());
		Object newObject = newInstance(newClass);
		srcDestMap.put(valFieldSrc, newObject);
		deepCopy(valFieldSrc, newObject);
		return newObject;
	}

	private Map<String, Field> prepareMap(Object srcObj) {
		Field[] srcFields = getAllFields(srcObj);
		Map<String, Field> map = new HashMap<String, Field>();
		for (Field f : srcFields) {
			map.put(f.getName(), f);
		}
		return map;
	}

	private Class<?> getMappedClass(Class<? extends Object> class1)
			throws ClassNotFoundException {
		String oldPackageStructure = "com.kiq.omega";
		if (class1.getName().startsWith("java")
				|| !class1.getName().startsWith(oldPackageStructure)) {
			return class1;
		}
		int indexOfComKiqOmega = class1.getName().indexOf(oldPackageStructure);
		if (indexOfComKiqOmega != -1) {
			// System.out.println(class1.getName());
			String newClassName = class1.getName().substring(
					indexOfComKiqOmega + oldPackageStructure.length() + 1);
			// System.out.println("loading :" + "com.pega.pad." + newClassName);
			return Class.forName("com.pega.pad." + newClassName);
		}
		return class1;
	}

	private Field[] getAllFields(Object obj) {
		Class<?> cls = obj.getClass();
		List<Field> accum = new LinkedList<Field>();
		while (cls != null) {
			Field[] f = cls.getDeclaredFields();
			for (int i = 0; i < f.length; i++) {
				accum.add(f[i]);
			}
			cls = cls.getSuperclass();
		}
		Field[] allFields = (Field[]) accum.toArray(new Field[accum.size()]);
		return allFields;
	}
}
