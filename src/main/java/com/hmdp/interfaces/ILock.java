package com.hmdp.interfaces;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-12 16:16
 */

public interface ILock {
    boolean tryLock(long timeoutSec);
    boolean unlock();
}
