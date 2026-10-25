package com.koushik.invoice.common.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.common.error.BadRequestException;

/**
 * Validated list request: {@code limit} in 1..100 (default 50) and an opaque cursor that wraps the last id
 * of the previous page. Ids are UUID v7, so ordering by id is ordering by creation time.
 */
public record PageQuery(int limit, @Nullable UUID after) {

	public static final int DEFAULT_LIMIT = 50;

	public static final int MAX_LIMIT = 100;

	public static PageQuery of(@Nullable Integer limit, @Nullable String cursor) {
		int effective = limit == null ? DEFAULT_LIMIT : limit;
		if (effective < 1 || effective > MAX_LIMIT) {
			throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT + ", was " + effective);
		}
		return new PageQuery(effective, cursor == null || cursor.isBlank() ? null : decode(cursor));
	}

	/**
	 * Builds the page from rows fetched with {@code limit + 1}: the extra row only signals that another page
	 * exists and is not returned.
	 */
	public <E, T> CursorPage<T> toPage(List<E> rowsPlusOne, Function<E, UUID> idOf, Function<E, T> mapper) {
		boolean hasMore = rowsPlusOne.size() > limit;
		List<E> rows = hasMore ? rowsPlusOne.subList(0, limit) : rowsPlusOne;
		String next = hasMore ? encode(idOf.apply(rows.getLast())) : null;
		return new CursorPage<>(rows.stream().map(mapper).toList(), limit, next);
	}

	private static String encode(UUID id) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static UUID decode(String cursor) {
		try {
			return UUID.fromString(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("Invalid cursor");
		}
	}

}
