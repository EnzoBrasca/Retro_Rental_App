import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '../../constants/theme';

/** Spinner centrado para el estado de carga de una pantalla. */
export function Loading() {
  return (
    <View style={styles.center}>
      <ActivityIndicator color={colors.primary} />
    </View>
  );
}

/** Mensaje de error con botón de reintento. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <View style={styles.center}>
      <Text style={styles.errorText}>{message}</Text>
      {onRetry && (
        <Pressable style={styles.retry} onPress={onRetry}>
          <Text style={styles.retryText}>Reintentar</Text>
        </Pressable>
      )}
    </View>
  );
}

/** Texto tenue para listas vacías. */
export function EmptyState({ message }: { message: string }) {
  return <Text style={styles.empty}>{message}</Text>;
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 30, gap: 14 },
  errorText: {
    color: colors.danger,
    fontSize: 14,
    textAlign: 'center',
    fontFamily: fonts.sans,
    lineHeight: 20,
  },
  retry: {
    backgroundColor: colors.primary,
    borderRadius: 10,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  retryText: { color: colors.bgDeep, fontFamily: fonts.sansSemi, fontSize: 14 },
  empty: { color: colors.textFaint, fontFamily: fonts.sans, marginBottom: 12 },
});
