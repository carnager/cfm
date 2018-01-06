package Cfm::Ui::Selector::Selector;
use strict;
use warnings FATAL => 'all';
use Moo;

use Cfm::Autowire;
use Cfm::Ui::Format::Pretty;
use Term::Form;

sub numerical_select {
    my ($data_cb, $show_data_cb, $extract_elements_cb) = @_;

    $extract_elements_cb //= sub {$_[0]->elements};
    my $page = 0;
    my $form = Term::Form->new("numerical_select");

    my $data = $data_cb->($page);
    while (1) {
        print "\n";
        $show_data_cb->($data);
        my $elements = $extract_elements_cb->($data);
        my $count = scalar $elements->@*;
        my $selection = $form->readline("Choose [1..$count/p/n/q]: ");

        return undef unless defined $selection;
        if ($selection =~ /\s*n\s*/) {
            $page++;
            $data = $data_cb->($page);
            next;
        } elsif ($selection =~ /\s*p\s*/) {
            $page--;
            $data = $data_cb->($page);
            next;
        } elsif ($selection =~ /\s*q\s*/) {
            return undef;
        } elsif ($selection =~ /^\s*\d+\s*$/) {
            next if $selection < 1 || $selection > scalar $elements->@*;
            return $elements->[int($selection) - 1];
        }
    };
}

1;
