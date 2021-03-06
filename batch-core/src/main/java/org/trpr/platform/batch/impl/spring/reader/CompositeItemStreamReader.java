/*
 * Copyright 2012-2015, the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trpr.platform.batch.impl.spring.reader;

import java.util.LinkedList;
import java.util.Queue;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.trpr.platform.batch.spi.spring.reader.BatchItemStreamReader;

/**
 * The <code>CompositeItemStreamReader</code> class is an implementation of the {@link BatchItemStreamReader} that implements the Composite design
 * pattern of delegating all operations to an BatchItemStreamReader and caching the read data in a bounded collection. This implementation may be used when the 
 * delegate BatchItemStreamReader does not inherently support data reading as streams. The size of the bounded collection is determined by the delegate
 * {@link BatchItemStreamReader#batchRead()} return data size.
 * 
 * @author Regunath B
 * @version 1.0, 28 Aug 2012
 */
public class CompositeItemStreamReader<T> implements BatchItemStreamReader<T>, InitializingBean {
	
	/** The delegate that does the actual data reading*/
	private BatchItemStreamReader<T> delegate;
	
	/** The bounded queue containing data items*/
	private Queue<T> boundedQueue = new LinkedList<T>();
	
	/**
	 * Constructor for this class
	 * @param delegate the ItemStreamReader delegate
	 */
	public CompositeItemStreamReader(BatchItemStreamReader<T> delegate) {
		this.delegate = delegate;
	}

	/**
	 * Interface method implementation. Returns data from the local bounded collection. Reads data from the delegate if the bounded collection is empty and
	 * populates the local bounded collection.
	 * @see org.springframework.batch.item.ItemReader#read()
	 */
	public synchronized T read() throws Exception, UnexpectedInputException, ParseException {
		if (this.boundedQueue.isEmpty()) {
			T[] items = this.delegate.batchRead();
			if (items == null) { // no more data to read
				return null;
			}
			for (T item : items) {
				this.boundedQueue.add(item);
			}
		}
		return this.boundedQueue.remove();
	}

	/**
	 * Interface method implementation. Calls the namesake method on the delegate.
	 * @see BatchItemStreamReader#batchRead()
	 */
	public T[] batchRead() throws Exception, UnexpectedInputException, ParseException {
		return this.delegate.batchRead();
	}

	
	/**
	 * Interface method implementation. Calls the namesake method on the delegate
	 * @see org.springframework.batch.item.ItemStream#close()
	 */
	public void close() throws ItemStreamException {
		this.delegate.close();
	}

	/**
	 * Interface method implementation. Calls the namesake method on the delegate
	 * @see org.springframework.batch.item.ItemStream#open(org.springframework.batch.item.ExecutionContext)
	 */
	public void open(ExecutionContext context) throws ItemStreamException {
		this.delegate.open(context);
	}

	/**
	 * Interface method implementation. Calls the namesake method on the delegate
	 * @see org.springframework.batch.item.ItemStream#update(org.springframework.batch.item.ExecutionContext)
	 */
	public void update(ExecutionContext context) throws ItemStreamException {
		this.delegate.update(context);
	}
	
	/**
	 * Interface method implementation. Ensures that the BatchItemStreamReader delegate has been set and is not null
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(delegate, "The 'delegate' may not be null");
	}	
	
	/** Getter/setter methods */
	public BatchItemStreamReader<T> getDelegate() {
		return this.delegate;
	}
	/** End getter/setter methods */


}
