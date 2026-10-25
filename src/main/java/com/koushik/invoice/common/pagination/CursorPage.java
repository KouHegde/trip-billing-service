package com.koushik.invoice.common.pagination;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** One page of a keyset-paginated list. {@code nextCursor} is null on the last page. */
public record CursorPage<T>(List<T> items, int limit, @Nullable String nextCursor) {
}
