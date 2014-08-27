// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.json;

import jodd.introspector.ClassDescriptor;
import jodd.introspector.ClassIntrospector;
import jodd.introspector.FieldDescriptor;
import jodd.introspector.Getter;
import jodd.introspector.PropertyDescriptor;
import jodd.json.meta.JsonAnnotationManager;

import java.lang.reflect.Modifier;

/**
 * Type's property visitor that follows JSON include/excludes rules.
 */
public abstract class TypeJsonVisitor {

	protected final JsonContext jsonContext;
	protected final boolean declared;
	protected final String classMetadataName;
	protected final Class type;

	protected int count;
	protected final JsonAnnotationManager.TypeData typeData;

	public TypeJsonVisitor(JsonContext jsonContext, Class type) {
		this.jsonContext = jsonContext;
		this.count = 0;
		this.declared = false;
		this.classMetadataName = jsonContext.jsonSerializer.classMetadataName;

		this.type = type;

		typeData = JoddJson.annotationManager.lookupTypeData(type);
	}

	/**
	 * Visits a type.
	 */
	public void visit() {
		ClassDescriptor classDescriptor = ClassIntrospector.lookup(type);

		if (classMetadataName != null) {
			// process first 'meta' fields 'class'
			onProperty(classMetadataName, null, false);
		}

		PropertyDescriptor[] propertyDescriptors = classDescriptor.getAllPropertyDescriptors();

		for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {

			Getter getter = propertyDescriptor.getGetter(declared);
			if (getter != null) {
				String propertyName = propertyDescriptor.getName();

				boolean isTransient = false;
				// check for transient flag
				FieldDescriptor fieldDescriptor = propertyDescriptor.getFieldDescriptor();

				if (fieldDescriptor != null) {
					isTransient = Modifier.isTransient(fieldDescriptor.getField().getModifiers());
				}

				onProperty(propertyName, propertyDescriptor, isTransient);
			}
		}
	}

	/**
	 * Invoked on each property. Properties are getting matched against the rules.
	 * If property passes all the rules, it will be processed in
	 * {@link #onSerializableProperty(String, jodd.introspector.PropertyDescriptor)}.
	 */
	protected void onProperty(
			String propertyName,
			PropertyDescriptor propertyDescriptor,
			boolean isTransient) {

		Class propertyType = propertyDescriptor == null ?  null : propertyDescriptor.getType();

		Path currentPath = jsonContext.path;

		currentPath.push(propertyName);

		// determine if name should be included/excluded

		boolean include = !typeData.strict;

		// + don't include transient fields

		if (isTransient) {
			include = false;
		}

		// + all collections are not serialized by default

		include = jsonContext.matchIgnoredPropertyTypes(propertyType, include);

		// + annotations

		include = typeData.rules.apply(propertyName, true, include);

		// + path queries: excludes/includes

		include = jsonContext.matchPathToQueries(include);

		// done

		if (!include) {
			currentPath.pop();
			return;
		}

		if (propertyType != null) {
			// change name for properties
			propertyName = typeData.resolveJsonName(propertyName);
		}

		onSerializableProperty(propertyName, propertyDescriptor);

		currentPath.pop();
	}

	/**
	 * Invoked on serializable properties, that have passed all the rules.
	 * Property descriptor may be <code>null</code> in special case when
	 * class meta data name is used.
	 */
	protected abstract void onSerializableProperty(String propertyName, PropertyDescriptor propertyDescriptor);

}