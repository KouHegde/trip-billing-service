package com.koushik.invoice.transaction.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.koushik.invoice.common.error.BadRequestException;
import com.koushik.invoice.transaction.application.dto.metadata.TransactionMetadata;
import com.koushik.invoice.transaction.application.handler.TransactionHandlerRegistry;
import com.koushik.invoice.transaction.application.handler.TransactionTypeHandler;
import com.koushik.invoice.transaction.domain.TransactionType;

/** Turns raw {@code metadata} JSON into the type's validated record, or fails with a 400 listing every problem. */
@Component
class MetadataParser {

	private final JsonMapper jsonMapper;

	private final Validator validator;

	private final TransactionHandlerRegistry handlers;

	MetadataParser(JsonMapper jsonMapper, Validator validator, TransactionHandlerRegistry handlers) {
		this.jsonMapper = jsonMapper;
		this.validator = validator;
		this.handlers = handlers;
	}

	TransactionMetadata parse(TransactionType type, JsonNode node) {
		if (node == null || !node.isObject()) {
			throw new BadRequestException("metadata must be a JSON object");
		}
		TransactionTypeHandler handler = handlers.handlerFor(type);
		TransactionMetadata metadata;
		try {
			metadata = jsonMapper.treeToValue(node, handler.metadataType());
		}
		catch (JacksonException ex) {
			throw new BadRequestException("Invalid metadata for type " + type.code() + ": " + ex.getOriginalMessage());
		}
		List<String> errors = new ArrayList<>(validator.validate(metadata)
			.stream()
			.sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
			.map(MetadataParser::describe)
			.toList());
		if (errors.isEmpty()) {
			errors.addAll(handler.crossFieldErrors(metadata));
		}
		if (!errors.isEmpty()) {
			throw new BadRequestException("Invalid metadata for type " + type.code(), errors);
		}
		return metadata;
	}

	private static String describe(ConstraintViolation<?> violation) {
		return "metadata." + violation.getPropertyPath() + ": " + violation.getMessage();
	}

}
