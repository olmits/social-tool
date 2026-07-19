// The modal slot is empty on any route that isn't the intercepted connect route
// (and on hard navigation / refresh), so the overlay is simply absent.
export default function ModalDefault() {
  return null;
}
