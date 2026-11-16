import pygame
import random
import math
import os

pygame.init()

FPS = 60

WIDTH, HEIGHT = 600, 700
TOP_OFFSET = 100
ROWS = 4
COLUMNS = 4

RECT_HEIGHT = (HEIGHT - TOP_OFFSET) // ROWS
RECT_WIDTH = WIDTH // COLUMNS

OUTLINE_COLOR = (170, 60, 207)
OUTLINE_THICKNESS = 10
BACKGROUND_COLOR = (11, 2, 19)
FONT_COLOR = (250, 232, 255)

FONT = pygame.font.SysFont("comicsans", 60, bold=True)
BUTTON_FONT = pygame.font.SysFont("comicsans", 20, bold=True)
SCORE_FONT = pygame.font.SysFont("comicsans", 24, bold=True)

MOVE_VEL = 25

WINDOW = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("2048")

BEST_SCORE_FILE = "best_score.txt"

def load_best_score():
    if os.path.exists(BEST_SCORE_FILE):
        with open(BEST_SCORE_FILE, "r") as f:
            try:
                return int(f.read().strip())
            except:
                return 0
    return 0

def save_best_score(score):
    with open(BEST_SCORE_FILE, "w") as f:
        f.write(str(score))


class Tile:
    COLORS = [
        (234, 171, 255),
        (194, 154, 202),
        (196, 140, 209),
        (166, 137, 206),
        (152, 105, 195),
        (129, 83, 172),
        (122, 66, 138),
        (99, 65, 113),
        (76, 53, 93),
        (60, 40, 70),
        (40, 20, 50),
    ]

    def __init__(self, value, row, col):
        self.value = value
        self.row = row
        self.col = col
        self.x = col * RECT_WIDTH
        self.y = TOP_OFFSET + row * RECT_HEIGHT

    def get_color(self):
        color_index = int(math.log2(self.value)) - 1
        if color_index < len(self.COLORS):
            return self.COLORS[color_index]
        return (40, 20, 50)

    def draw(self, window):
        color = self.get_color()
        pygame.draw.rect(window, color, (self.x, self.y, RECT_WIDTH, RECT_HEIGHT))

        text = FONT.render(str(self.value), True, FONT_COLOR)
        window.blit(text,
                    (self.x + RECT_WIDTH // 2 - text.get_width() // 2,
                     self.y + RECT_HEIGHT // 2 - text.get_height() // 2))

    def set_position(self, ceil=False):
        if ceil:
            self.row = math.ceil((self.y - TOP_OFFSET) / RECT_HEIGHT)
            self.col = math.ceil(self.x / RECT_WIDTH)
        else:
            self.row = math.floor((self.y - TOP_OFFSET) / RECT_HEIGHT)
            self.col = math.floor(self.x / RECT_WIDTH)

    def move(self, delta):
        self.x += delta[0]
        self.y += delta[1]


class Button:
    def __init__(self, text, x, y, width, height, color, hover_color, action=None):
        self.text = text
        self.rect = pygame.Rect(x, y, width, height)
        self.color = color
        self.hover_color = hover_color
        self.action = action
        self.is_hovered = False
        self.enabled = True

    def draw(self, surface):
        color = self.hover_color if self.is_hovered and self.enabled else self.color
        pygame.draw.rect(surface, color, self.rect, border_radius=5)
        pygame.draw.rect(surface, (170, 60, 207), self.rect, 2, border_radius=5)
        text_surface = BUTTON_FONT.render(self.text, True, (255, 255, 255))
        text_rect = text_surface.get_rect(center=self.rect.center)
        surface.blit(text_surface, text_rect)

    def handle_event(self, event):
        if not self.enabled:
            return False
        if event.type == pygame.MOUSEMOTION:
            self.is_hovered = self.rect.collidepoint(event.pos)
        elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            if self.rect.collidepoint(event.pos) and self.action:
                self.action()
                return True
        return False

    def set_enabled(self, enabled):
        self.enabled = enabled
        if not enabled:
            self.is_hovered = False


def draw_grid(window):
    pygame.draw.rect(window, OUTLINE_COLOR,
                     (0, TOP_OFFSET, WIDTH, HEIGHT - TOP_OFFSET), OUTLINE_THICKNESS)

    for row in range(1, ROWS):
        y = TOP_OFFSET + row * RECT_HEIGHT
        pygame.draw.line(window, OUTLINE_COLOR, (0, y), (WIDTH, y), OUTLINE_THICKNESS)

    for col in range(1, COLUMNS):
        x = col * RECT_WIDTH
        pygame.draw.line(window, OUTLINE_COLOR, (x, TOP_OFFSET), (x, HEIGHT), OUTLINE_THICKNESS)


def has_legal_moves(tiles):
    if len(tiles) < 16:
        return True

    for row in range(ROWS):
        for col in range(COLUMNS - 1):
            key1 = f"{row}{col}"
            key2 = f"{row}{col + 1}"
            if key1 in tiles and key2 in tiles and tiles[key1].value == tiles[key2].value:
                return True

    for col in range(COLUMNS):
        for row in range(ROWS - 1):
            key1 = f"{row}{col}"
            key2 = f"{row + 1}{col}"
            if key1 in tiles and key2 in tiles and tiles[key1].value == tiles[key2].value:
                return True

    return False


def draw_top_bar(window, score, best_score, buttons):
    left_x = 20
    score_text = SCORE_FONT.render(f"Score: {score}", True, FONT_COLOR)
    best_text = SCORE_FONT.render(f"Best: {best_score}", True, FONT_COLOR)
    center_y = TOP_OFFSET // 2
    window.blit(score_text, (left_x, center_y - score_text.get_height()))
    window.blit(best_text, (left_x, center_y + 5))

    if buttons is None:
        return
    for button in buttons:
        button.draw(window)


def draw(window, tiles, score, best_score, buttons):
    window.fill(BACKGROUND_COLOR)

    for tile in tiles.values():
        tile.draw(window)

    draw_grid(window)

    if not has_legal_moves(tiles):
        overlay = pygame.Surface((WIDTH, HEIGHT - TOP_OFFSET), pygame.SRCALPHA)
        overlay.fill((0, 0, 0, 128))
        window.blit(overlay, (0, TOP_OFFSET))
        game_over_text = FONT.render("Game Over", True, (255, 255, 255))
        window.blit(game_over_text,
                    (WIDTH // 2 - game_over_text.get_width() // 2,
                     TOP_OFFSET + (HEIGHT - TOP_OFFSET) // 2 - game_over_text.get_height() // 2))

    draw_top_bar(window, score, best_score, buttons)

    pygame.display.update()


def get_random_position(tiles):
    while True:
        row = random.randint(0, ROWS - 1)
        col = random.randint(0, COLUMNS - 1)
        if f"{row}{col}" not in tiles:
            return row, col


def move_tiles(window, tiles, clock, direction, score, best_score, buttons):
    updated = True
    blocks = set()
    score_gain = 0

    if direction == "left":
        sort_function = lambda x: x.col
        reverse = False
        delta = (-MOVE_VEL, 0)
        boundary_check = lambda tile: tile.col == 0
        get_next_tile = lambda tile: tiles.get(f"{tile.row}{tile.col - 1}", None)
        merge_check = lambda tile, next_tile: tile.x > next_tile.x + MOVE_VEL
        move_check = lambda tile, next_tile: tile.x > next_tile.x + RECT_WIDTH
        ceil = True
    elif direction == "right":
        sort_function = lambda x: x.col
        reverse = True
        delta = (MOVE_VEL, 0)
        boundary_check = lambda tile: tile.col == COLUMNS - 1
        get_next_tile = lambda tile: tiles.get(f"{tile.row}{tile.col + 1}", None)
        merge_check = lambda tile, next_tile: tile.x < next_tile.x - MOVE_VEL
        move_check = lambda tile, next_tile: tile.x < next_tile.x - RECT_WIDTH
        ceil = False
    elif direction == "up":
        sort_function = lambda x: x.row
        reverse = False
        delta = (0, -MOVE_VEL)
        boundary_check = lambda tile: tile.row == 0
        get_next_tile = lambda tile: tiles.get(f"{tile.row - 1}{tile.col}", None)
        merge_check = lambda tile, next_tile: tile.y > next_tile.y + MOVE_VEL
        move_check = lambda tile, next_tile: tile.y > next_tile.y + RECT_HEIGHT
        ceil = True
    elif direction == "down":
        sort_function = lambda x: x.row
        reverse = True
        delta = (0, MOVE_VEL)
        boundary_check = lambda tile: tile.row == ROWS - 1
        get_next_tile = lambda tile: tiles.get(f"{tile.row + 1}{tile.col}", None)
        merge_check = lambda tile, next_tile: tile.y < next_tile.y - MOVE_VEL
        move_check = lambda tile, next_tile: tile.y < next_tile.y - RECT_HEIGHT
        ceil = False
    else:
        return "continue", 0, False

    before = sorted(snapshot_tiles(tiles))
    sorted_tiles = []
    while updated:
        clock.tick(FPS)
        updated = False
        sorted_tiles = sorted(tiles.values(), key=sort_function, reverse=reverse)

        i = 0
        while i < len(sorted_tiles):
            tile = sorted_tiles[i]

            if boundary_check(tile):
                i += 1
                continue

            # Draw the entire window including top bar
            draw(window, tiles, score, best_score, buttons)

            next_tile = get_next_tile(tile)
            old_key = f"{tile.row}{tile.col}"

            if not next_tile:
                tile.move(delta)
                tile.set_position(ceil)
                new_key = f"{tile.row}{tile.col}"
                if old_key != new_key:
                    tiles[new_key] = tiles.pop(old_key)
                updated = True
                i += 1

            elif (tile.value == next_tile.value and
                  tile not in blocks and next_tile not in blocks):
                if merge_check(tile, next_tile):
                    tile.move(delta)
                    tile.set_position(ceil)
                    new_key = f"{tile.row}{tile.col}"
                    if old_key != new_key:
                        tiles[new_key] = tiles.pop(old_key)
                    updated = True
                    i += 1
                else:
                    next_tile.value *= 2
                    score_gain += next_tile.value
                    del tiles[old_key]
                    sorted_tiles.pop(i)
                    blocks.add(next_tile)
                    updated = True

            elif move_check(tile, next_tile):
                tile.move(delta)
                tile.set_position(ceil)
                new_key = f"{tile.row}{tile.col}"
                if old_key != new_key:
                    tiles[new_key] = tiles.pop(old_key)
                updated = True
                i += 1
            else:
                i += 1

    tiles.clear()
    for tile in sorted_tiles:
        tiles[f"{tile.row}{tile.col}"] = tile

    # No tile slid or merged: don't spawn
    if sorted(snapshot_tiles(tiles)) == before:
        return "continue", 0, False

    return end_tiles(tiles), score_gain, True


def end_tiles(tiles):
    if len(tiles) == 16:
        return "lost"

    row, col = get_random_position(tiles)
    tiles[f"{row}{col}"] = Tile(random.choices([2, 4], weights=[9, 1])[0], row, col)
    return "continue"


def generate_tiles():
    tiles = {}
    for _ in range(2):
        row, col = get_random_position(tiles)
        tiles[f"{row}{col}"] = Tile(2, row, col)
    return tiles


def snapshot_tiles(tiles):
    return [(tile.value, tile.row, tile.col) for tile in tiles.values()]


def restore_snapshot(snapshot):
    tiles = {}
    for value, row, col in snapshot:
        tiles[f"{row}{col}"] = Tile(value, row, col)
    return tiles


def main(window):
    clock = pygame.time.Clock()
    run = True

    tiles = generate_tiles()
    score = 0
    best_score = load_best_score()

    last_state = None

    button_width, button_height = 120, 50
    spacing = 30
    total_width_buttons = 2 * button_width + spacing
    right_half_start = WIDTH // 2
    start_x = right_half_start + (right_half_start - total_width_buttons) // 2
    button_y = (TOP_OFFSET - button_height) // 2

    def new_game_action():
        nonlocal tiles, score, last_state, best_score
        tiles = generate_tiles()
        score = 0
        last_state = None
        undo_button.set_enabled(False)

    def undo_action():
        nonlocal tiles, score, last_state
        if last_state is not None:
            snapshot, score_before = last_state
            tiles = restore_snapshot(snapshot)
            score = score_before
            last_state = None
            undo_button.set_enabled(False)

    new_button = Button("NEW", start_x, button_y, button_width, button_height,
                        (150, 79, 179), (205, 143, 227), new_game_action)

    undo_button = Button("UNDO", start_x + button_width + spacing, button_y, button_width, button_height,
                         (150, 79, 179), (205, 143, 227), undo_action)

    undo_button.set_enabled(False)

    buttons = [new_button, undo_button]

    while run:
        clock.tick(FPS)

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                run = False
                break

            for button in buttons:
                button.handle_event(event)

            if event.type == pygame.KEYDOWN:
                if event.key in (pygame.K_LEFT, pygame.K_RIGHT, pygame.K_UP, pygame.K_DOWN):
                    if has_legal_moves(tiles):
                        before = snapshot_tiles(tiles)
                        score_before = score

                        dir_map = {
                            pygame.K_LEFT: "left",
                            pygame.K_RIGHT: "right",
                            pygame.K_UP: "up",
                            pygame.K_DOWN: "down",
                        }
                        direction = dir_map[event.key]
                        # Pass score, best_score, and buttons to keep top bar visible
                        status, gain, moved = move_tiles(window, tiles, clock, direction,
                                                         score, best_score, buttons)

                        score += gain
                        if score > best_score:
                            best_score = score
                            save_best_score(best_score)

                        if moved:
                            last_state = (before, score_before)
                            undo_button.set_enabled(True)
                        else:
                            score = score_before

        draw(window, tiles, score, best_score, buttons)

    pygame.quit()


if __name__ == "__main__":
    main(WINDOW)