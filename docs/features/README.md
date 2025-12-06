# Feature Documentation

Documentation for features implemented in the K language.

## String Support

Complete implementation of string types with Z3 string theory integration:

- **[STRING_SUPPORT.md](STRING_SUPPORT.md)** - Basic string type support
- **[STRING_CONCATENATION.md](STRING_CONCATENATION.md)** - String concatenation with `+` operator
- **[STRING_OPERATIONS.md](STRING_OPERATIONS.md)** - String operations (length, substring, contains, etc.)
- **[STRING_IMPLEMENTATION_COMPLETE.md](STRING_IMPLEMENTATION_COMPLETE.md)** - Complete feature summary

## Usage Examples

See the test files in `src/test/` for working examples:
- `SimpleStringTest.k` - Basic string constraints
- `StringConcatTest.k` - String concatenation
- `StringOperationsTest.k` - Full string operations demo

Run examples with:
```bash
./export/k src/test/SimpleStringTest.k
```

