package tree_sitter.c.node

import tree_sitter.Language

public class TSCLanguage(
    public val lang: Language,
) {
    public val _abstract_declarator: UShort = lang.idForNodeKind("_abstract_declarator", true)

    public val _declarator: UShort = lang.idForNodeKind("_declarator", true)

    public val _field_declarator: UShort = lang.idForNodeKind("_field_declarator", true)

    public val _type_declarator: UShort = lang.idForNodeKind("_type_declarator", true)

    public val expression: UShort = lang.idForNodeKind("expression", true)

    public val statement: UShort = lang.idForNodeKind("statement", true)

    public val type_specifier: UShort = lang.idForNodeKind("type_specifier", true)

    public val abstract_array_declarator: UShort = lang.idForNodeKind(
        "abstract_array_declarator",
        true
    )

    public val abstract_function_declarator: UShort =
        lang.idForNodeKind("abstract_function_declarator", true)

    public val abstract_parenthesized_declarator: UShort =
        lang.idForNodeKind("abstract_parenthesized_declarator", true)

    public val abstract_pointer_declarator: UShort = lang.idForNodeKind(
        "abstract_pointer_declarator",
        true
    )

    public val alignas_qualifier: UShort = lang.idForNodeKind("alignas_qualifier", true)

    public val alignof_expression: UShort = lang.idForNodeKind("alignof_expression", true)

    public val argument_list: UShort = lang.idForNodeKind("argument_list", true)

    public val array_declarator: UShort = lang.idForNodeKind("array_declarator", true)

    public val assignment_expression: UShort = lang.idForNodeKind("assignment_expression", true)

    public val attribute: UShort = lang.idForNodeKind("attribute", true)

    public val attribute_declaration: UShort = lang.idForNodeKind("attribute_declaration", true)

    public val attribute_specifier: UShort = lang.idForNodeKind("attribute_specifier", true)

    public val attributed_declarator: UShort = lang.idForNodeKind("attributed_declarator", true)

    public val attributed_statement: UShort = lang.idForNodeKind("attributed_statement", true)

    public val binary_expression: UShort = lang.idForNodeKind("binary_expression", true)

    public val bitfield_clause: UShort = lang.idForNodeKind("bitfield_clause", true)

    public val break_statement: UShort = lang.idForNodeKind("break_statement", true)

    public val call_expression: UShort = lang.idForNodeKind("call_expression", true)

    public val case_statement: UShort = lang.idForNodeKind("case_statement", true)

    public val cast_expression: UShort = lang.idForNodeKind("cast_expression", true)

    public val char_literal: UShort = lang.idForNodeKind("char_literal", true)

    public val comma_expression: UShort = lang.idForNodeKind("comma_expression", true)

    public val compound_literal_expression: UShort = lang.idForNodeKind(
        "compound_literal_expression",
        true
    )

    public val compound_statement: UShort = lang.idForNodeKind("compound_statement", true)

    public val concatenated_string: UShort = lang.idForNodeKind("concatenated_string", true)

    public val conditional_expression: UShort = lang.idForNodeKind("conditional_expression", true)

    public val continue_statement: UShort = lang.idForNodeKind("continue_statement", true)

    public val declaration: UShort = lang.idForNodeKind("declaration", true)

    public val declaration_list: UShort = lang.idForNodeKind("declaration_list", true)

    public val do_statement: UShort = lang.idForNodeKind("do_statement", true)

    public val else_clause: UShort = lang.idForNodeKind("else_clause", true)

    public val enum_specifier: UShort = lang.idForNodeKind("enum_specifier", true)

    public val enumerator: UShort = lang.idForNodeKind("enumerator", true)

    public val enumerator_list: UShort = lang.idForNodeKind("enumerator_list", true)

    public val expression_statement: UShort = lang.idForNodeKind("expression_statement", true)

    public val extension_expression: UShort = lang.idForNodeKind("extension_expression", true)

    public val field_declaration: UShort = lang.idForNodeKind("field_declaration", true)

    public val field_declaration_list: UShort = lang.idForNodeKind("field_declaration_list", true)

    public val field_designator: UShort = lang.idForNodeKind("field_designator", true)

    public val field_expression: UShort = lang.idForNodeKind("field_expression", true)

    public val for_statement: UShort = lang.idForNodeKind("for_statement", true)

    public val function_declarator: UShort = lang.idForNodeKind("function_declarator", true)

    public val function_definition: UShort = lang.idForNodeKind("function_definition", true)

    public val generic_expression: UShort = lang.idForNodeKind("generic_expression", true)

    public val gnu_asm_clobber_list: UShort = lang.idForNodeKind("gnu_asm_clobber_list", true)

    public val gnu_asm_expression: UShort = lang.idForNodeKind("gnu_asm_expression", true)

    public val gnu_asm_goto_list: UShort = lang.idForNodeKind("gnu_asm_goto_list", true)

    public val gnu_asm_input_operand: UShort = lang.idForNodeKind("gnu_asm_input_operand", true)

    public val gnu_asm_input_operand_list: UShort = lang.idForNodeKind(
        "gnu_asm_input_operand_list",
        true
    )

    public val gnu_asm_output_operand: UShort = lang.idForNodeKind("gnu_asm_output_operand", true)

    public val gnu_asm_output_operand_list: UShort = lang.idForNodeKind(
        "gnu_asm_output_operand_list",
        true
    )

    public val gnu_asm_qualifier: UShort = lang.idForNodeKind("gnu_asm_qualifier", true)

    public val goto_statement: UShort = lang.idForNodeKind("goto_statement", true)

    public val if_statement: UShort = lang.idForNodeKind("if_statement", true)

    public val init_declarator: UShort = lang.idForNodeKind("init_declarator", true)

    public val initializer_list: UShort = lang.idForNodeKind("initializer_list", true)

    public val initializer_pair: UShort = lang.idForNodeKind("initializer_pair", true)

    public val labeled_statement: UShort = lang.idForNodeKind("labeled_statement", true)

    public val linkage_specification: UShort = lang.idForNodeKind("linkage_specification", true)

    public val macro_type_specifier: UShort = lang.idForNodeKind("macro_type_specifier", true)

    public val ms_based_modifier: UShort = lang.idForNodeKind("ms_based_modifier", true)

    public val ms_call_modifier: UShort = lang.idForNodeKind("ms_call_modifier", true)

    public val ms_declspec_modifier: UShort = lang.idForNodeKind("ms_declspec_modifier", true)

    public val ms_pointer_modifier: UShort = lang.idForNodeKind("ms_pointer_modifier", true)

    public val ms_unaligned_ptr_modifier: UShort = lang.idForNodeKind(
        "ms_unaligned_ptr_modifier",
        true
    )

    public val `null`: UShort = lang.idForNodeKind("null", true)

    public val offsetof_expression: UShort = lang.idForNodeKind("offsetof_expression", true)

    public val parameter_declaration: UShort = lang.idForNodeKind("parameter_declaration", true)

    public val parameter_list: UShort = lang.idForNodeKind("parameter_list", true)

    public val parenthesized_declarator: UShort = lang.idForNodeKind("parenthesized_declarator", true)

    public val parenthesized_expression: UShort = lang.idForNodeKind("parenthesized_expression", true)

    public val pointer_declarator: UShort = lang.idForNodeKind("pointer_declarator", true)

    public val pointer_expression: UShort = lang.idForNodeKind("pointer_expression", true)

    public val preproc_call: UShort = lang.idForNodeKind("preproc_call", true)

    public val preproc_def: UShort = lang.idForNodeKind("preproc_def", true)

    public val preproc_defined: UShort = lang.idForNodeKind("preproc_defined", true)

    public val preproc_elif: UShort = lang.idForNodeKind("preproc_elif", true)

    public val preproc_elifdef: UShort = lang.idForNodeKind("preproc_elifdef", true)

    public val preproc_else: UShort = lang.idForNodeKind("preproc_else", true)

    public val preproc_function_def: UShort = lang.idForNodeKind("preproc_function_def", true)

    public val preproc_if: UShort = lang.idForNodeKind("preproc_if", true)

    public val preproc_ifdef: UShort = lang.idForNodeKind("preproc_ifdef", true)

    public val preproc_include: UShort = lang.idForNodeKind("preproc_include", true)

    public val preproc_params: UShort = lang.idForNodeKind("preproc_params", true)

    public val return_statement: UShort = lang.idForNodeKind("return_statement", true)

    public val seh_except_clause: UShort = lang.idForNodeKind("seh_except_clause", true)

    public val seh_finally_clause: UShort = lang.idForNodeKind("seh_finally_clause", true)

    public val seh_leave_statement: UShort = lang.idForNodeKind("seh_leave_statement", true)

    public val seh_try_statement: UShort = lang.idForNodeKind("seh_try_statement", true)

    public val sized_type_specifier: UShort = lang.idForNodeKind("sized_type_specifier", true)

    public val sizeof_expression: UShort = lang.idForNodeKind("sizeof_expression", true)

    public val storage_class_specifier: UShort = lang.idForNodeKind("storage_class_specifier", true)

    public val string_literal: UShort = lang.idForNodeKind("string_literal", true)

    public val struct_specifier: UShort = lang.idForNodeKind("struct_specifier", true)

    public val subscript_designator: UShort = lang.idForNodeKind("subscript_designator", true)

    public val subscript_expression: UShort = lang.idForNodeKind("subscript_expression", true)

    public val subscript_range_designator: UShort = lang.idForNodeKind(
        "subscript_range_designator",
        true
    )

    public val switch_statement: UShort = lang.idForNodeKind("switch_statement", true)

    public val translation_unit: UShort = lang.idForNodeKind("translation_unit", true)

    public val type_definition: UShort = lang.idForNodeKind("type_definition", true)

    public val type_descriptor: UShort = lang.idForNodeKind("type_descriptor", true)

    public val type_qualifier: UShort = lang.idForNodeKind("type_qualifier", true)

    public val unary_expression: UShort = lang.idForNodeKind("unary_expression", true)

    public val union_specifier: UShort = lang.idForNodeKind("union_specifier", true)

    public val update_expression: UShort = lang.idForNodeKind("update_expression", true)

    public val variadic_parameter: UShort = lang.idForNodeKind("variadic_parameter", true)

    public val while_statement: UShort = lang.idForNodeKind("while_statement", true)

    public val character: UShort = lang.idForNodeKind("character", true)

    public val comment: UShort = lang.idForNodeKind("comment", true)

    public val escape_sequence: UShort = lang.idForNodeKind("escape_sequence", true)

    public val `false`: UShort = lang.idForNodeKind("false", true)

    public val field_identifier: UShort = lang.idForNodeKind("field_identifier", true)

    public val identifier: UShort = lang.idForNodeKind("identifier", true)

    public val ms_restrict_modifier: UShort = lang.idForNodeKind("ms_restrict_modifier", true)

    public val ms_signed_ptr_modifier: UShort = lang.idForNodeKind("ms_signed_ptr_modifier", true)

    public val ms_unsigned_ptr_modifier: UShort = lang.idForNodeKind("ms_unsigned_ptr_modifier", true)

    public val number_literal: UShort = lang.idForNodeKind("number_literal", true)

    public val preproc_arg: UShort = lang.idForNodeKind("preproc_arg", true)

    public val preproc_directive: UShort = lang.idForNodeKind("preproc_directive", true)

    public val primitive_type: UShort = lang.idForNodeKind("primitive_type", true)

    public val statement_identifier: UShort = lang.idForNodeKind("statement_identifier", true)

    public val string_content: UShort = lang.idForNodeKind("string_content", true)

    public val system_lib_string: UShort = lang.idForNodeKind("system_lib_string", true)

    public val `true`: UShort = lang.idForNodeKind("true", true)

    public val type_identifier: UShort = lang.idForNodeKind("type_identifier", true)

    public companion object {
        public val Lang: TSCLanguage = TSCLanguage(Language.getLanguage("c"))
    }
}
