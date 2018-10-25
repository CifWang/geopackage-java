package mil.nga.geopackage.extension.style;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mil.nga.sf.GeometryType;

/**
 * Styles for a single feature geometry or feature table default
 * 
 * @author osbornb
 * @since 3.1.1
 */
public class Style {

	/**
	 * Default style
	 */
	private StyleRow defaultStyle;

	/**
	 * Geometry type to style mapping
	 */
	private Map<GeometryType, StyleRow> styles = new HashMap<>();

	/**
	 * Set the default style icon
	 * 
	 * @param styleRow
	 *            default style
	 */
	public void setDefaultStyle(StyleRow styleRow) {
		setStyle(styleRow, null);
	}

	/**
	 * Set the style for the geometry type
	 * 
	 * @param styleRow
	 *            style row
	 * @param geometryType
	 *            geometry type
	 */
	public void setStyle(StyleRow styleRow, GeometryType geometryType) {
		if (geometryType != null) {
			if (styleRow != null) {
				styles.put(geometryType, styleRow);
			} else {
				styles.remove(geometryType);
			}
		} else {
			defaultStyle = styleRow;
		}
	}

	/**
	 * Default style
	 * 
	 * @return default style
	 */
	public StyleRow getDefaultStyle() {
		return defaultStyle;
	}

	/**
	 * Get an unmodifiable mapping between specific geometry types and styles
	 * 
	 * @return geometry types to style mapping
	 */
	public Map<GeometryType, StyleRow> getStyles() {
		return Collections.unmodifiableMap(styles);
	}

	/**
	 * Get the style, either the default or single geometry type style
	 * 
	 * @return style
	 */
	public StyleRow getStyle() {
		return getStyle(null);
	}

	/**
	 * Get the style for the geometry type
	 * 
	 * @param geometryType
	 *            geometry type
	 * @return style
	 */
	public StyleRow getStyle(GeometryType geometryType) {

		StyleRow styleRow = null;

		if (geometryType != null) {
			List<GeometryType> geometryTypes = FeatureStyles
					.getGeometryTypeInheritance(geometryType);
			for (GeometryType type : geometryTypes) {
				styleRow = styles.get(type);
				if (styleRow != null) {
					break;
				}
			}
		}

		if (styleRow == null) {
			styleRow = defaultStyle;
		}

		if (styleRow == null && geometryType == null && styles.size() == 1) {
			styleRow = styles.values().iterator().next();
		}

		return styleRow;
	}

}
