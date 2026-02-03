#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct node {
    char *s;
    struct node *next;
    struct node *prev;
};

void search(struct node *head, char *s);
void insert(struct node *head, char *s);
void print(struct node *head);
void delete(struct node *head, char *s);
struct node *init();
void release(struct node *head);


int main() {
    struct node *head = init();

    insert(head, "World!");
    insert(head, "Hello");
    insert(head, "This is my program!");
    print(head);

    search(head, "Hello");
    delete(head, "Hello");
    print(head);

    search(head, "Hello");

    release(head);
}

//initalize sentinel node
struct node *init() {
    struct node  *head = malloc(sizeof(struct node));

    head->s = 0;
    head->next = NULL;
    head->prev = NULL;
    return head;
}

void insert(struct node *head, char *s) {
    struct node *new_node = malloc(sizeof(struct node));

    new_node->s = malloc(sizeof(strlen(s)+1));      //allocate mem for string
    strcpy(new_node->s,s);

    if (head->next != NULL) {
        new_node->next = head->next;
        head->next->prev = new_node;
    }
    else {
        new_node->next = NULL;
    }
    new_node->prev = head;
    head->next = new_node;
}

void search(struct node *head, char *s) {
    struct node *temp = head->next;

    while (temp != NULL) {
        if (strcmp(temp->s,s) == 0) {
            printf("String \"%s\" found.\n", s);
            return;
        }
        else {
            temp = temp->next;
        }
    }
    printf("String \"%s\" not found.\n", s);
}

void delete(struct node *head, char *s){

    struct node *temp = head;
    struct node *save;

    while (temp != NULL) {
        if (strcmp(temp->next->s,s) == 0) {
            save = temp->next;
            temp->next->next->prev = temp;
            temp->next = temp->next->next;

            free(save->s);
            free(save);
            printf("String \"%s\" deleted.\n",s);
            return;
        }
        else {
            temp = temp->next;
        }
    }
    printf("String \"%s\" could not be found.\n",s);
}

void release(struct node *head) {
    struct node *temp = head;
    struct node *prev;

    while (temp != NULL){             //go through list and free previous node
        prev = temp;
        temp = temp->next;
        free(prev->s);
        free(prev);
    }
}

void print(struct node *head) {
    struct node *temp = head->next;

    while (temp != NULL) {
        printf("%s\n",temp->s);
        temp = temp->next;
    }
}