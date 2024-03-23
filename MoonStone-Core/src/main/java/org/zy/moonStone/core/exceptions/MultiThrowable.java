package org.zy.moonstone.core.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @dateTime 2022年9月22日;
 * @author zy(azurite-Y);
 * @description
 * 
 * 将可抛出对象列表包装为单个可抛出对象。当执行多个操作时，每个操作都可能抛出异常，但所有操作都在报告任何错误之前执行。
 * <p>
 * 这个类不是线程安全的
 */
public class MultiThrowable extends Throwable {
    private static final long serialVersionUID = 1L;

    private List<Throwable> throwables = new ArrayList<>();

    /**
     * 向包装的可抛出对象列表中添加可抛出对象
     *
     * @param t - 添加的可抛出对象
     */
    public void add(Throwable t) {
        throwables.add(t);
    }

    /**
     * @return 包装的可抛出对象的只读列表。
     */
    public List<Throwable> getThrowables() {
        return Collections.unmodifiableList(throwables);
    }

    /**
     * @return 如果没有包装的可抛出对象，则为 {@code null}; 如果有单个包装的可抛出对象，则为Throwable
     */
    public Throwable getThrowable() {
        if (size() == 0) {
            return null;
        } else if (size() == 1) {
            return throwables.get(0);
        } else {
            return this;
        }
    }

    /**
     * @return 此实例当前包装的可抛出对象的数量。
     */
    public int size() {
        return throwables.size();
    }

    /**
     * 重写默认实现，以提供与每个包装的可抛出对象关联的消息的串联。注意，返回String的格式不保证是固定的，可能在未来的版本中更改。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(": ");
        sb.append(size());
        sb.append(" wrapped Throwables: ");
        for (Throwable t : throwables) {
            sb.append("[");
            sb.append(t.getMessage());
            sb.append("]");
        }
        return sb.toString();
    }
}
