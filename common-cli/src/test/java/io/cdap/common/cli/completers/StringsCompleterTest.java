/*
 * Copyright © 2012-2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.common.cli.completers;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.Collection;

/**
 * Test for {@link StringsCompleter}.
 */
public class StringsCompleterTest extends CompleterTestBase {

  @Test
  public void testComplete() {
    StringsCompleter completer = new StringsCompleter() {
      @Override
      protected Supplier<Collection<String>> getStringsSupplier() {
        return Suppliers.<Collection<String>>ofInstance(ImmutableList.of("asdf", "asdd", "bdf"));
      }
    };

    testCompleter(completer, "a", 0, ImmutableList.<CharSequence>of("asdd", "asdf"));
    testCompleter(completer, "b", 0, ImmutableList.<CharSequence>of("bdf"));
    testCompleter(completer, "c", 0, ImmutableList.<CharSequence>of());
    testOrder(completer, "a", 0, ImmutableList.<CharSequence>of("asdd", "asdf"));

  }

}
