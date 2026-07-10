# FuelTrack — Mobile Mockup

React Native (Expo + TypeScript) port of the FuelTrack HTML prototype. Fleet &
fuel-load tracking for road-construction sites. No database — all data is static
mock data (`src/data/mock.ts`), designed to be swapped for a real API later.

## Run

```bash
npm install
npx expo start
```

Open in **Expo Go** (scan the QR) or press `i` (iOS simulator) / `a` (Android emulator).

> The camera is real hardware. Use **Expo Go on a physical device** to test capture;
> iOS Simulator has no camera.

## Demo accounts (role validation)

Login decides the role — there is no manual admin/employee toggle.

| Role      | Email                       | Password    | Lands on            |
| --------- | --------------------------- | ----------- | ------------------- |
| Employee  | `juan.perez@vialsur.com`    | `123456789` | Tab app (fleet)     |
| Admin     | `admin@vialsur.com`         | `admin123`  | Analytics dashboard |

## What works

- **Auth** — email/password with real validation against hardcoded accounts.
- **Role routing** — employee → bottom-tab app, admin → analytics dashboard.
- **Camera (expo-camera)** — the center shutter takes a real photo, shows a
  scan animation, then a prefilled (simulated OCR) form you can edit and confirm.
- **Fleet / History / Profile** — FlatList-based screens with the original design.
- **Admin analytics** — period filters (7d/30d/90d) + trend / provider / operator
  charts (react-native-svg).
- **Tutorial** — 4-step coach overlay, launchable from Home (`?`) or Profile.

## Structure

```
App.tsx                     fonts, providers, navigation container
src/theme/                  colors, fonts, radius tokens
src/data/mock.ts            all static data + analytics helpers
src/context/AuthContext.tsx login/logout + current user role
src/navigation/             RootNavigator (role switch) + EmployeeArea (tabs)
src/components/              Logo, charts, shared UI
src/screens/                Auth, Home, History, Scanner, Profile, Admin, Tutorial
```
