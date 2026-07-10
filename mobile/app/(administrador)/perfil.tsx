import { useRouter } from 'expo-router';
import { ProfileView } from '../../components/fuel/ProfileView';

/**
 * Perfil del administrador. Se abre desde el avatar del dashboard (no es una
 * tab), por eso pasa `onBack` para volver a la pantalla anterior.
 */
export default function AdminPerfilScreen() {
  const router = useRouter();
  return <ProfileView onBack={() => router.back()} />;
}
