package dev.tako.papersdelight.jug.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JugFluidExpression {

    private JugFluidExpression() {
    }

    static Object snapshot(Object expression) {
        if (expression instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), snapshot(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (expression instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object value : list) {
                copy.add(snapshot(value));
            }
            return Collections.unmodifiableList(copy);
        }
        return expression;
    }
}
