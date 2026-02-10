for testfile in tests/ill-typed/*; do
  # awk ищет строку, начинающуюся с "Error:", и печатает 2-е слово (код ошибки)
# Добавлено 2>&1 перед знаком |
verdict="$(java -jar ./target/stella-implementation-in-java-1.0-SNAPSHOT.jar < "$testfile" 2>&1 | awk '/^Error:/ {print $2}')"
  if [ -z "$verdict" ]; then
    echo '///ok' >> "$testfile"
  else
    echo "///$verdict" >> "$testfile"
  fi
done
