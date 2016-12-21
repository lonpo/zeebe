/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.tngp.log;

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.hashindex.Long2ByteHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Long2ByteArrayHashIndexTest
{
    private static final byte[] VALUE = "bar".getBytes();
    private static final byte[] ANOTHER_VALUE = "plo".getBytes();
    private static final byte[] MISSING_VALUE = "foo".getBytes();

    private Long2ByteHashIndex index;
    private FileChannelIndexStore indexStore;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void createIndex()
    {
        final int indexSize = 16;
        final int valueLength = 3 * SIZE_OF_BYTE;

        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2ByteHashIndex(indexStore, indexSize, 1, valueLength);
    }

    @After
    public void close()
    {
        indexStore.close();
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
        // given that the map is empty
        assertThat(index.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        index.put(1, VALUE);

        // then
        assertThat(index.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if then
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(VALUE);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if
        final byte[] removeResult = index.remove(1, MISSING_VALUE);

        //then
        assertThat(removeResult).isEqualTo(VALUE);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(MISSING_VALUE);
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        index.put(1, VALUE);
        index.put(2, ANOTHER_VALUE);

        // if
        final byte[] removeResult = index.remove(1, MISSING_VALUE);

        //then
        assertThat(removeResult).isEqualTo(VALUE);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(MISSING_VALUE);
        assertThat(index.get(2, MISSING_VALUE)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldSplit()
    {
        // given
        index.put(0, VALUE);

        // if
        index.put(1, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(2);
        assertThat(index.get(0, MISSING_VALUE)).isEqualTo(VALUE);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldSplitTwoTimes()
    {
        // given
        index.put(1, VALUE);
        assertThat(index.blockCount()).isEqualTo(1);

        // if
        index.put(3, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(3);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(VALUE);
        assertThat(index.get(3, MISSING_VALUE)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(i, VALUE);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(i, ANOTHER_VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, MISSING_VALUE)).isEqualTo(i % 2 == 0 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, i < 8 ? VALUE : ANOTHER_VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, MISSING_VALUE)).isEqualTo(i < 8 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(i, ANOTHER_VALUE)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void cannotPutValueIfIndexFull()
    {
        // given
        index.put(0, VALUE);

        thrown.expect(RuntimeException.class);

        index.put(16, ANOTHER_VALUE);
    }

    @Test
    public void cannotPutValueIfValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        index.put(0, "too long".getBytes());
    }

    @Test
    public void cannotGetValueIfMissingValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        index.get(0, "too long".getBytes());
    }

    @Test
    public void cannotRemoveValueIfMissingValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        index.remove(0, "too long".getBytes());
    }

}
