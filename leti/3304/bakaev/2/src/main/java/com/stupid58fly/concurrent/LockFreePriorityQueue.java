package com.stupid58fly.concurrent;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreePriorityQueue<E extends Comparable<E>> extends AbstractQueue<E> implements PriorityQueue<E>  {
    protected final Node<E> head;
    protected final Node<E> tail;

    public LockFreePriorityQueue() {
        this.head = new Node<>(null);
        this.tail = new Node<>(null);

        this.head.setNext(tail);
        this.tail.setNext(tail);
    }

    @Override
    public Iterator<E> iterator() {
        return new PriorityQueueIterator();
    }

    /**
     * Returns the number of elements in this queue. If this queue contains more than Integer.MAX_VALUE elements, returns Integer.MAX_VALUE.
     * Beware that, unlike in most collections, this method is NOT a constant-time operation. Because of the asynchronous nature of these queues, determining the current number of elements requires an O(n) traversal. Additionally, if elements are added or removed during execution of this method, the returned result may be inaccurate. Thus, this method is typically not very useful in concurrent applications.
     * @return the number of elements in this queue
     */
    @Override
    public int size() {
        for(;;) {
            int size = 0;

            for (Node<E> current = head; current != tail; ++size) {
                current = current.getNext();
                if (size == Integer.MAX_VALUE)
                    return size;
                if (current.isDeleted())
                    --size;
            }

            return size;
        }
    }

    @Override
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();

        Node<E> node = new Node<>(e);

        for(;;) {
            Position<E> position = find(e);

            node.setNext(position.getCurrent());

            if (position.getPreview().casNext(position.getCurrent(), node))
                return true;
        }
    }

    @Override
    public E poll() {
        for(;;) {
            Node<E> top = head.getNext();

            if (top.isDeleted()) {
                head.casNext(top, top.getNext());
                continue;
            }

            if (top != tail && !top.setDeleted(true)) {
                continue;
            }

            head.casNext(top, top.getNext());
            return top.getItem();
        }
    }

    @Override
    public E peek() {
        for(;;) {
            Node<E> top = head.getNext();

            if (top.isDeleted()) {
                head.casNext(top, top.getNext());
                continue;
            }

            return top.getItem();
        }
    }

    @Override
    public boolean isEmpty() {
        for(;;) {
            Node<E> top = head.getNext();

            if (top.isDeleted()) {
                head.casNext(top, top.getNext());
                continue;
            }

            return top == tail;
        }
    }

    protected Position<E> find(E item) {
        Position<E> position = new Position<>(head, head.getNext());

        while (true) {
            if (position.getCurrent().isDeleted()) {
                position.getPreview().casNext(position.getCurrent(), position.getCurrent().getNext());
            } else if (position.getCurrent() == tail || item.compareTo(position.getCurrent().getItem()) < 0) {
                return position;
            }

            position = new Position<>(position.getCurrent(), position.getCurrent().getNext());
        }
    }

    protected final class PriorityQueueIterator implements Iterator<E> {
        protected Node<E> currentNode = head;

        @Override
        public boolean hasNext() {
            return currentNode.getNext() != tail;
        }

        @Override
        public E next() {
            currentNode = currentNode.getNext();
            return currentNode.getItem();
        }
    }

    protected final class Position<E> {
        protected final Node<E> preview;
        protected final Node<E> current;

        public Position(Node<E> preview, Node<E> current) {
            this.preview = preview;
            this.current = current;
        }

        public Node<E> getPreview() {
            return preview;
        }

        public Node<E> getCurrent() {
            return current;
        }
    }

    protected final class Node<E> {
        protected final E item;
        protected final AtomicMarkableReference<Node<E>> nextRef;

        public Node(E item) {
            this.item = item;
            this.nextRef = new AtomicMarkableReference<>(null, false);
        }

        public E getItem() {
            return item;
        }

        public Node<E> getNext() {
            return nextRef.getReference();
        }

        public void setNext(Node<E> next) {
            nextRef.set(next, false);
        }

        public boolean casNext(Node<E> expected, Node<E> update) {
            return nextRef.compareAndSet(expected, update, false, false);
        }

        public boolean isDeleted() {
            return nextRef.isMarked();
        }

        public boolean setDeleted(boolean deleted) {
            Node<E> next = getNext();
            return nextRef.compareAndSet(next, next, !deleted, deleted);
        }
    }
}
